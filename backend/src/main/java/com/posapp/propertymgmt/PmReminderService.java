package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtReminderDtos.*;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.RentChargeRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rent reminders and late fees. Unpaid rent is e-mailed to the tenant before it falls due and, once late, every few days up to a limit; rent that stays unpaid past a grace period can
 * be charged a late fee (a percentage of what is unpaid, or a fixed amount), invoiced to the tenant. The morning job does both when they are switched on; the Rent screen can do either
 * by hand for one charge. Every reminder is recorded, so nobody is reminded twice for the same thing, and a late fee is charged at most once per rent charge.
 */
@Service
public class PmReminderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}$");
    /** Most e-mails one run sends, so a run cannot go on for minutes. The rest go on the next run. */
    static final int MAX_PER_RUN = 200;

    private final PmSettingsService settings;
    private final PmRentChargeRepository charges;
    private final PmRentReminderRepository reminders;
    private final PmLateFeeRepository fees;
    private final PmRentService rent;
    private final PmViews views;
    private final PmTenantService tenantService;
    private final InvoiceService invoiceService;
    private final CatalogService catalogService;
    private final MailService mailService;
    private final PmTenantDocuments documents;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;

    public PmReminderService(PmSettingsService settings, PmRentChargeRepository charges, PmRentReminderRepository reminders, PmLateFeeRepository fees, PmRentService rent, PmViews views,
                             PmTenantService tenantService, InvoiceService invoiceService, CatalogService catalogService, MailService mailService, PmTenantDocuments documents,
                             PropertyManagementAccess access, AuditLogService auditLogService, PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.charges = charges;
        this.reminders = reminders;
        this.fees = fees;
        this.rent = rent;
        this.views = views;
        this.tenantService = tenantService;
        this.invoiceService = invoiceService;
        this.catalogService = catalogService;
        this.mailService = mailService;
        this.documents = documents;
        this.access = access;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ settings

    private ReminderSettingsDto dto(PmSettings s) {
        return new ReminderSettingsDto(s.isRemindersEnabled(), s.getReminderBeforeDays(), s.getOverdueFirstDays(), s.getOverdueRepeatDays(), s.getOverdueMaxReminders(), s.isLateFeeEnabled(),
                s.isLateFeeAuto(), s.getLateFeeGraceDays(), s.getLateFeeType(), s.getLateFeeValue(), s.getLateFeeMax(), s.getRemindersLastRun(), s.getRemindersLastSummary(), mailService.isConfigured());
    }

    @Transactional
    public ReminderSettingsDto settings() {
        access.on();
        return dto(settings.entity());
    }

    private static int range(Integer v, int lo, int hi, String what) {
        if (v < lo || v > hi) throw ApiException.badRequest(what + " must be between " + lo + " and " + hi + ".");
        return v;
    }

    @Transactional
    public ReminderSettingsDto saveSettings(ReminderSettingsRequest r) {
        access.on();
        PmSettings s = settings.entity();
        if (r.reminderBeforeDays() != null) s.setReminderBeforeDays(range(r.reminderBeforeDays(), 0, 30, "Days before the due date"));
        if (r.overdueFirstDays() != null) s.setOverdueFirstDays(range(r.overdueFirstDays(), 0, 90, "Days after the due date for the first reminder"));
        if (r.overdueRepeatDays() != null) s.setOverdueRepeatDays(range(r.overdueRepeatDays(), 0, 90, "Days between reminders"));
        if (r.overdueMaxReminders() != null) s.setOverdueMaxReminders(range(r.overdueMaxReminders(), 1, 12, "The most reminders for one late rent"));
        if (r.lateFeeGraceDays() != null) s.setLateFeeGraceDays(range(r.lateFeeGraceDays(), 0, 90, "The grace period"));
        if (r.lateFeeType() != null) {
            String t = r.lateFeeType().trim().toUpperCase();
            if (!t.equals("PERCENT") && !t.equals("FIXED")) throw ApiException.badRequest("A late fee is a percentage of the unpaid rent or a fixed amount.");
            s.setLateFeeType(t);
        }
        if (r.lateFeeValue() != null) {
            if (r.lateFeeValue().signum() <= 0) throw ApiException.badRequest("The late fee must be more than zero.");
            if (r.lateFeeValue().compareTo(new BigDecimal("1000000000000")) > 0) throw ApiException.badRequest("That late fee is too large.");
            s.setLateFeeValue(r.lateFeeValue().setScale(2, RoundingMode.HALF_UP));
        }
        if (Boolean.TRUE.equals(r.clearLateFeeMax())) s.setLateFeeMax(null);
        else if (r.lateFeeMax() != null) {
            if (r.lateFeeMax().signum() <= 0) throw ApiException.badRequest("The most a late fee can come to must be more than zero.");
            s.setLateFeeMax(r.lateFeeMax().setScale(2, RoundingMode.HALF_UP));
        }
        if ("PERCENT".equals(s.getLateFeeType()) && s.getLateFeeValue() != null && s.getLateFeeValue().compareTo(HUNDRED) > 0) throw ApiException.badRequest("A percentage late fee is at most 100 percent.");
        if (r.remindersEnabled() != null) s.setRemindersEnabled(r.remindersEnabled());
        if (r.lateFeeEnabled() != null) {
            s.setLateFeeEnabled(r.lateFeeEnabled());
            if (!r.lateFeeEnabled()) s.setLateFeeAuto(false);
        }
        if (r.lateFeeAuto() != null) s.setLateFeeAuto(r.lateFeeAuto());
        if (s.isLateFeeEnabled() && s.getLateFeeValue() == null) throw ApiException.badRequest("Give the late fee (a percentage or an amount) before switching late fees on.");
        if (s.isLateFeeAuto() && !s.isLateFeeEnabled()) throw ApiException.badRequest("Switch late fees on first. Charging them automatically builds on it.");
        settings.save(s);
        auditLogService.record("PM_REMINDER_SETTINGS", "Changed the rent reminder and late fee settings");
        return dto(s);
    }

    // ------------------------------------------------------------------ the plan

    private static LocalDate dayOf(Instant i) {
        return i.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static BigDecimal feeFor(PmSettings st, BigDecimal outstanding) {
        if (st.getLateFeeValue() == null || outstanding == null) return ZERO;
        BigDecimal amount = "FIXED".equals(st.getLateFeeType()) ? st.getLateFeeValue() : outstanding.multiply(st.getLateFeeValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        if ("PERCENT".equals(st.getLateFeeType()) && st.getLateFeeMax() != null && amount.compareTo(st.getLateFeeMax()) > 0) amount = st.getLateFeeMax();
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** What is still owed on the rent itself: the invoice also carries utilities and service charges, and the tenant's payments go to the rent first, so a late fee is worked out on this. */
    static BigDecimal rentOutstanding(RentChargeRow r) {
        BigDecimal out = r.outstanding() == null ? ZERO : r.outstanding();
        BigDecimal extras = r.extras() == null ? ZERO : r.extras();
        return out.subtract(out.min(extras));
    }

    private static boolean unpaid(RentChargeRow r) {
        return r.invoiceId() != null && r.outstanding() != null && r.outstanding().signum() > 0 && ("UNPAID".equals(r.status()) || "PARTLY_PAID".equals(r.status()) || "OVERDUE".equals(r.status()));
    }

    /** What would be done today for each unpaid, invoiced rent charge. Rows with nothing to do are left out. */
    private List<PlanRow> plan(Snapshot s, PmSettings st) {
        List<PmRentCharge> open = charges.findByStatus(RentChargeStatus.INVOICED).stream().filter(c -> !c.isSettled()).toList();
        if (open.isEmpty()) return List.of();
        List<RentChargeRow> rows = rent.rows(open, s, true);
        List<Long> ids = rows.stream().map(RentChargeRow::id).toList();
        Map<Long, List<PmRentReminder>> sent = new HashMap<>();
        for (PmRentReminder x : reminders.findByRentChargeIdInOrderBySentAtAsc(ids)) sent.computeIfAbsent(x.getRentChargeId(), k -> new ArrayList<>()).add(x);
        Set<Long> feed = new java.util.HashSet<>();
        for (PmLateFee f : fees.findByRentChargeIdIn(ids)) feed.add(f.getRentChargeId());
        boolean mail = mailService.isConfigured();
        List<PlanRow> out = new ArrayList<>();
        for (RentChargeRow r : rows) {
            if (!unpaid(r)) continue;
            List<PmRentReminder> mine = sent.getOrDefault(r.id(), List.of());
            boolean today = mine.stream().anyMatch(x -> dayOf(x.getSentAt()).equals(s.today));
            long toDue = java.time.temporal.ChronoUnit.DAYS.between(s.today, r.dueDate());
            String kind = null;
            if (st.isRemindersEnabled() && !today) {
                if (toDue >= 0 && st.getReminderBeforeDays() > 0 && toDue <= st.getReminderBeforeDays() && mine.stream().noneMatch(x -> x.getKind() == PmRentReminder.Kind.BEFORE)) kind = "BEFORE";
                else if (r.daysOverdue() != null && st.getOverdueFirstDays() > 0 && r.daysOverdue() >= st.getOverdueFirstDays()) {
                    List<PmRentReminder> late = mine.stream().filter(x -> x.getKind() == PmRentReminder.Kind.OVERDUE).toList();
                    boolean room = late.size() < st.getOverdueMaxReminders();
                    boolean due = late.isEmpty() || st.getOverdueRepeatDays() > 0 && !dayOf(late.get(late.size() - 1).getSentAt()).plusDays(st.getOverdueRepeatDays()).isAfter(s.today);
                    if (room && due) kind = "OVERDUE";
                }
            }
            BigDecimal fee = null;
            if (st.isLateFeeEnabled() && st.isLateFeeAuto() && r.daysOverdue() != null && r.daysOverdue() > st.getLateFeeGraceDays() && !feed.contains(r.id())) {
                BigDecimal f = feeFor(st, rentOutstanding(r));
                if (f.signum() > 0) fee = f;
            }
            if (kind == null && fee == null) continue;
            PmTenant t = s.tenants.get(r.tenantId());
            String email = t == null || t.getEmail() == null ? null : t.getEmail().trim();
            String problem = null;
            if (kind != null) {
                if (email == null || email.isEmpty()) problem = "No e-mail address for " + r.tenantName() + ".";
                else if (!EMAIL.matcher(email).matches()) problem = "The e-mail address of " + r.tenantName() + " does not look right: " + email;
                else if (!mail) problem = "E-mail is not set up yet.";
            }
            out.add(new PlanRow(r.id(), r.leaseId(), r.leaseCode(), r.tenantName(), email, (r.propertyName() == null ? "" : r.propertyName() + " · ") + r.unitNo(), r.invoiceNumber(), r.dueDate(),
                    r.outstanding(), r.daysOverdue(), kind, fee, problem));
        }
        out.sort(Comparator.comparing(PlanRow::dueDate).thenComparing(PlanRow::chargeId));
        return out;
    }

    /** What the morning job would do if it ran now. */
    @Transactional
    public List<PlanRow> due() {
        access.on();
        return plan(views.snapshot(), settings.entity());
    }

    // ------------------------------------------------------------------ the morning run

    /** Sends the reminders and charges the late fees that are due today, as the settings say. Each charge is its own transaction, so one failure holds up nothing else. */
    public RunResult run(String by) {
        access.on();
        PmSettings st = settings.entity();
        Snapshot s = views.snapshot();
        List<PlanRow> todo = plan(s, st);
        int reminded = 0, feesCharged = 0, skipped = 0, failed = 0;
        List<String> notes = new ArrayList<>();
        for (PlanRow p : todo) {
            String who = p.leaseCode() + " (" + p.tenantName() + ", " + p.invoiceNumber() + ")";
            if (p.lateFee() != null) {
                try {
                    tx.execute(x -> chargeFee(p.chargeId(), true, by));
                    feesCharged++;
                } catch (RuntimeException e) {
                    failed++;
                    if (notes.size() < 50) notes.add(who + ": late fee not charged: " + message(e));
                }
            }
            if (p.reminder() != null) {
                if (p.problem() != null) {
                    skipped++;
                    if (notes.size() < 50) notes.add(who + ": " + p.problem());
                } else if (reminded + failed >= MAX_PER_RUN) {
                    skipped++;
                } else {
                    try {
                        tx.execute(x -> send(p.chargeId(), PmRentReminder.Kind.valueOf(p.reminder()), by, null, null));
                        reminded++;
                    } catch (RuntimeException e) {
                        failed++;
                        if (notes.size() < 50) notes.add(who + ": " + message(e));
                    }
                }
            }
        }
        String summary = reminded + " reminder" + (reminded == 1 ? "" : "s") + " sent, " + feesCharged + " late fee" + (feesCharged == 1 ? "" : "s") + " charged"
                + (skipped > 0 ? ", " + skipped + " skipped" : "") + (failed > 0 ? ", " + failed + " failed" : "");
        tx.executeWithoutResult(x -> {
            PmSettings fresh = settings.entity();
            fresh.setRemindersLastRun(LocalDate.now());
            fresh.setRemindersLastSummary(summary);
            settings.save(fresh);
        });
        if (reminded > 0 || feesCharged > 0 || failed > 0) auditLogService.record("PM_REMINDERS_RUN", by + ": " + summary);
        return new RunResult(reminded, feesCharged, skipped, failed, notes);
    }

    private static String message(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ------------------------------------------------------------------ one reminder

    private PmRentCharge find(Long id) {
        return charges.findById(id).orElseThrow(() -> ApiException.notFound("Rent charge not found: " + id));
    }

    private RentChargeRow rowOf(PmRentCharge c, Snapshot s) {
        return rent.rows(List.of(c), s, true).get(0);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** E-mails the tenant about this unpaid rent and records it. Fails with a plain message when it cannot go. */
    private ReminderSent send(Long chargeId, PmRentReminder.Kind kind, String by, String override, String note) {
        PmRentCharge c = find(chargeId);
        Snapshot s = views.snapshot();
        RentChargeRow r = rowOf(c, s);
        if (c.getStatus() != RentChargeStatus.INVOICED || !unpaid(r)) throw ApiException.conflict("This rent is not waiting to be paid, so there is nothing to remind about.");
        PmTenant t = s.tenants.get(r.tenantId());
        String to = override != null && !override.isBlank() ? override.trim() : t == null || t.getEmail() == null ? "" : t.getEmail().trim();
        if (to.isEmpty()) throw ApiException.badRequest("The tenant has no e-mail address. Type one in, or add it on the tenant's page.");
        if (!EMAIL.matcher(to).matches()) throw ApiException.badRequest("That e-mail address does not look right: " + to);
        if (!mailService.isConfigured()) throw ApiException.badRequest("E-mail is not set up yet. Set up SMTP in Settings > Email first.");
        if (note != null && note.trim().length() > 1000) throw ApiException.badRequest("The message is too long (at most 1000 characters).");
        PmSettings st = settings.entity();
        PmLateFee fee = fees.findByRentChargeId(chargeId).orElse(null);
        String business = documents.businessName();
        boolean late = r.daysOverdue() != null;
        String amount = r.outstanding().toPlainString();
        String subject = (late ? "Overdue rent: " + amount + " was due on " + r.dueDate().format(PmTenantAccountService.DAY) : "Rent reminder: " + amount + " is due on " + r.dueDate().format(PmTenantAccountService.DAY))
                + " - " + business;
        StringBuilder b = new StringBuilder();
        b.append("<p>Dear ").append(esc(r.tenantName())).append(",</p>");
        if (note != null && !note.trim().isEmpty()) b.append("<p>").append(esc(note.trim()).replace("\n", "<br/>")).append("</p>");
        if (late) b.append("<p>Our records show that your rent for <strong>").append(esc(r.unitNo())).append("</strong> is <strong>").append(r.daysOverdue()).append(" day").append(r.daysOverdue() == 1 ? "" : "s")
                .append(" overdue</strong>.</p>");
        else b.append("<p>This is a friendly reminder that your rent for <strong>").append(esc(r.unitNo())).append("</strong> falls due soon.</p>");
        b.append("<table cellpadding=\"4\" style=\"border-collapse:collapse;\">");
        row(b, "Property", (r.propertyName() == null ? "" : r.propertyName() + ", ") + "unit " + r.unitNo());
        row(b, "Lease", r.leaseCode());
        row(b, "Rent for", r.periodStart().format(PmTenantAccountService.DAY) + " to " + r.periodEnd().format(PmTenantAccountService.DAY));
        row(b, "Invoice", r.invoiceNumber());
        row(b, "Due on", r.dueDate().format(PmTenantAccountService.DAY));
        row(b, late ? "Amount overdue" : "Amount to pay", "<strong>" + amount + "</strong>");
        b.append("</table>");
        if (fee != null) b.append("<p>A late fee of <strong>").append(fee.getAmount().toPlainString()).append("</strong> has been added (invoice ").append(esc(fee.getInvoiceNumber())).append(").</p>");
        else if (st.isLateFeeEnabled() && st.getLateFeeValue() != null && late) b.append("<p>Please note that a late fee of ").append(feeText(st)).append(" may be charged on rent that stays unpaid.</p>");
        else if (st.isLateFeeEnabled() && st.getLateFeeValue() != null) b.append("<p>Please note that a late fee of ").append(feeText(st)).append(" may be charged on rent that is more than ").append(st.getLateFeeGraceDays())
                .append(" days late.</p>");
        b.append("<p>Your invoice is attached (PDF). If you have already paid, please ignore this message and accept our thanks.</p>");
        b.append("<p>Regards,<br/>").append(esc(business)).append(documents.contactLine().isEmpty() ? "" : "<br/>" + esc(documents.contactLine())).append("</p>");
        byte[] pdf = invoiceService.pdf(r.invoiceId());
        mailService.send(to, subject, b.toString(), List.of(new EmailAttachment(r.invoiceNumber() + ".pdf", "application/pdf", pdf)));
        PmRentReminder x = new PmRentReminder();
        x.setRentChargeId(chargeId);
        x.setSentAt(Instant.now());
        x.setKind(kind);
        x.setSentTo(to);
        x.setSentBy(by);
        reminders.save(x);
        return new ReminderSent(to);
    }

    private static void row(StringBuilder b, String label, String value) {
        b.append("<tr><td>").append(esc(label)).append("</td><td>").append(value.startsWith("<strong>") ? value : esc(value)).append("</td></tr>");
    }

    private static String feeText(PmSettings st) {
        String v = st.getLateFeeValue().stripTrailingZeros().toPlainString();
        return "FIXED".equals(st.getLateFeeType()) ? v : v + "% of the unpaid rent" + (st.getLateFeeMax() == null ? "" : " (at most " + st.getLateFeeMax().toPlainString() + ")");
    }

    /** A reminder sent by hand, to the tenant's address or one typed in, with an optional message. */
    @Transactional
    public ReminderSent remind(Actor a, Long chargeId, RemindRequest r) {
        access.on();
        ReminderSent sent = send(chargeId, PmRentReminder.Kind.MANUAL, PropertyManagementAccess.name(a.user()), r == null ? null : r.email(), r == null ? null : r.message());
        auditLogService.record("PM_RENT_REMINDER", "E-mailed a rent reminder to " + sent.sentTo(), "PmRentCharge", chargeId);
        return sent;
    }

    @Transactional
    public List<ReminderRow> history(Long chargeId) {
        access.on();
        return reminders.findByRentChargeIdOrderBySentAtDesc(chargeId).stream().map(x -> new ReminderRow(x.getSentAt(), x.getKind().name(), x.getSentTo(), x.getSentBy())).toList();
    }

    // ------------------------------------------------------------------ late fees

    @Transactional
    public Product lateFeeProduct() {
        PmSettings st = settings.entity();
        if (st.getLateFeeProductId() != null) {
            try {
                return catalogService.getProductOrThrow(st.getLateFeeProductId());
            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            }
        }
        ProductRequest req = new ProductRequest();
        req.setName("Late rent fee");
        req.setDescription("Fee for rent paid late");
        req.setUnitPrice(ZERO);
        req.setCostPrice(ZERO);
        req.setStockQuantity(0);
        req.setTrackStock(false);
        req.setService(true);
        req.setActive(true);
        Product created = catalogService.getProductOrThrow(catalogService.createProduct(req).id());
        st.setLateFeeProductId(created.getId());
        settings.save(st);
        return created;
    }

    /** Invoices the late fee on one overdue rent charge to the tenant, once. {@code auto} is true when the morning job does it (the grace period was checked by the plan). */
    private LateFeeDto chargeFee(Long chargeId, boolean auto, String by) {
        PmRentCharge c = find(chargeId);
        PmSettings st = settings.entity();
        if (!st.isLateFeeEnabled()) throw ApiException.conflict("Late fees are switched off in Settings.");
        if (st.getLateFeeValue() == null) throw ApiException.conflict("Give the late fee (a percentage or an amount) in Settings first.");
        if (fees.findByRentChargeId(chargeId).isPresent()) throw ApiException.conflict("A late fee was already charged on this rent.");
        Snapshot s = views.snapshot();
        RentChargeRow r = rowOf(c, s);
        if (!unpaid(r) || r.daysOverdue() == null) throw ApiException.conflict("Only rent that is unpaid and past its due date can be charged a late fee.");
        BigDecimal amount = feeFor(st, rentOutstanding(r));
        if (amount.signum() <= 0) throw ApiException.conflict("The late fee comes to nothing on this rent.");
        PmTenant t = tenantService.entity(r.tenantId());
        Customer cust = tenantService.ensureCustomer(t);
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(cust.getId());
        req.setDueDate(LocalDate.now().plusDays(7));
        req.setNotes("Late fee for rent invoice " + r.invoiceNumber() + " (due " + r.dueDate() + ", lease " + r.leaseCode() + ")");
        InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
        line.setProductId(lateFeeProduct().getId());
        line.setQuantity(1);
        line.setUnitPrice(amount);
        line.setDescription("Late fee: rent " + r.invoiceNumber() + " due " + r.dueDate() + ", " + r.daysOverdue() + " days late (" + (r.propertyName() == null ? "" : r.propertyName() + ", ") + "unit " + r.unitNo() + ")");
        req.setLines(List.of(line));
        InvoiceDto inv = invoiceService.issue(invoiceService.createDraft(req).id());
        PmLateFee f = new PmLateFee();
        f.setRentChargeId(chargeId);
        f.setAmount(amount);
        f.setChargedOn(LocalDate.now());
        f.setAuto(auto);
        f.setInvoiceId(inv.id());
        f.setInvoiceNumber(inv.invoiceNumber());
        f.setChargedBy(by);
        fees.save(f);
        auditLogService.record("PM_LATE_FEE", by + " charged a late fee of " + amount.toPlainString() + " to " + r.tenantName() + " on rent " + r.invoiceNumber() + " (" + inv.invoiceNumber() + ")", "PmRentCharge", chargeId);
        return new LateFeeDto(chargeId, amount, inv.invoiceNumber(), f.getChargedOn());
    }

    /** A late fee charged by hand on one overdue rent, whatever the grace period says. */
    @Transactional
    public LateFeeDto lateFee(Actor a, Long chargeId) {
        access.on();
        return chargeFee(chargeId, false, PropertyManagementAccess.name(a.user()));
    }
}
