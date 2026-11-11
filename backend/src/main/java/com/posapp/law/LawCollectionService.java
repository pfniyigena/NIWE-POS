package com.posapp.law;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.InvoiceService;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawCollectionDtos.Aging;
import com.posapp.law.dto.LawCollectionDtos.CollectionRow;
import com.posapp.law.dto.LawCollectionDtos.CollectionsDto;
import com.posapp.law.dto.LawCollectionDtos.FeeLine;
import com.posapp.law.dto.LawCollectionDtos.FeeStatementData;
import com.posapp.law.dto.LawCollectionDtos.RemindRequest;
import com.posapp.law.dto.LawCollectionDtos.RemindResult;
import com.posapp.law.dto.LawCollectionDtos.ReminderItem;
import com.posapp.law.dto.LawPrintDtos.MailedClient;
import com.posapp.law.dto.LawPrintDtos.Printed;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Collecting the firm's fees: the unpaid invoices of the matters a person may see, with their age; a client's fee statement (printed or e-mailed); and reminder
 * e-mails about overdue invoices, by hand or once a day when the firm switches that on. An invoice with no due date is due after the client's credit term, or the
 * firm's default term. One reminder mail goes to a client for all their overdue invoices, with their statement attached.
 */
@Service
public class LawCollectionService {

    private static final Logger log = LoggerFactory.getLogger(LawCollectionService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final LawAccess access;
    private final LawBillRepository billRepository;
    private final LawMatterRepository matterRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceService invoiceService;
    private final LawService lawService;
    private final LawPrintFiles files;
    private final MailService mailService;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;

    public LawCollectionService(LawAccess access, LawBillRepository billRepository, LawMatterRepository matterRepository, CustomerRepository customerRepository,
                                InvoiceService invoiceService, LawService lawService, LawPrintFiles files, MailService mailService, ModuleService moduleService,
                                AuditLogService auditLogService) {
        this.access = access;
        this.billRepository = billRepository;
        this.matterRepository = matterRepository;
        this.customerRepository = customerRepository;
        this.invoiceService = invoiceService;
        this.lawService = lawService;
        this.files = files;
        this.mailService = mailService;
        this.moduleService = moduleService;
        this.auditLogService = auditLogService;
    }

    private record Item(LawBill bill, LawMatter m, InvoiceDto inv, Customer customer, LocalDate date, LocalDate due, boolean assumed, BigDecimal outstanding, BigDecimal paid,
                        BigDecimal total, long daysOverdue) {
        boolean open() {
            return outstanding.signum() > 0;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    /** Every issued invoice of a matter the predicate accepts (paid or not). */
    private List<Item> items(Predicate<LawMatter> include, LocalDate today) {
        int defTerm = lawService.settingsEntity().getDefaultTermDays();
        Map<Long, LawMatter> matters = matterRepository.findAll().stream().collect(Collectors.toMap(LawMatter::getId, m -> m));
        Map<Long, Customer> customers = new HashMap<>();
        List<Item> out = new ArrayList<>();
        for (LawBill b : billRepository.findByStatus(BillStatus.ACTIVE)) {
            if (b.getInvoiceId() == null) continue;
            LawMatter m = matters.get(b.getMatterId());
            if (m == null || !include.test(m)) continue;
            InvoiceDto inv;
            try {
                inv = invoiceService.getById(b.getInvoiceId());
            } catch (Exception e) {
                continue;
            }
            if (!"ISSUED".equals(inv.status())) continue;
            Customer c = m.getClientCustomerId() == null ? null : customers.computeIfAbsent(m.getClientCustomerId(), id -> customerRepository.findById(id).orElse(null));
            LocalDate date = inv.invoiceDate() == null ? today : inv.invoiceDate().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate due = inv.dueDate();
            boolean assumed = false;
            if (due == null) {
                int term = c != null && c.getDefaultCreditTermDays() != null ? c.getDefaultCreditTermDays() : defTerm;
                due = date.plusDays(term);
                assumed = true;
            }
            BigDecimal total = nz(inv.totalAmount());
            BigDecimal paid = nz(inv.amountPaid());
            BigDecimal outstanding = inv.amountOutstanding() != null ? inv.amountOutstanding() : total.subtract(paid);
            long days = outstanding.signum() > 0 && due.isBefore(today) ? ChronoUnit.DAYS.between(due, today) : 0;
            out.add(new Item(b, m, inv, c, date, due, assumed, outstanding, paid, total, days));
        }
        return out;
    }

    private static Aging aging(List<Item> open) {
        BigDecimal cur = ZERO, a = ZERO, b = ZERO, c = ZERO, d = ZERO;
        for (Item i : open) {
            if (i.daysOverdue() == 0) cur = cur.add(i.outstanding());
            else if (i.daysOverdue() <= 30) a = a.add(i.outstanding());
            else if (i.daysOverdue() <= 60) b = b.add(i.outstanding());
            else if (i.daysOverdue() <= 90) c = c.add(i.outstanding());
            else d = d.add(i.outstanding());
        }
        return new Aging(cur, a, b, c, d);
    }

    private static CollectionRow row(Item i) {
        LawBill b = i.bill();
        return new CollectionRow(b.getId(), b.getInvoiceId(), b.getInvoiceNumber(), i.m().getId(), i.m().getNumber(), i.m().getTitle(), i.m().getClientCustomerId(), i.m().getClientName(),
                i.customer() != null ? i.customer().getEmail() : null, i.date(), i.due(), i.assumed(), i.total(), i.paid(), i.outstanding(), i.daysOverdue(), b.getReminderCount(), b.getLastRemindedAt());
    }

    public static boolean canSend(Actor a, boolean canBill) {
        return a.admin() || canBill || a.manager();
    }

    // ------------------------------------------------------------------ the list

    @Transactional(readOnly = true)
    public CollectionsDto list(Actor a, boolean canBill, boolean overdueOnly, String q, Long clientId) {
        access.on();
        LocalDate today = LocalDate.now();
        List<Item> open = items(m -> access.canView(m, a), today).stream().filter(Item::open).toList();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<CollectionRow> rows = open.stream()
                .filter(i -> !overdueOnly || i.daysOverdue() > 0)
                .filter(i -> clientId == null || clientId.equals(i.m().getClientCustomerId()))
                .filter(i -> needle.isEmpty() || (i.m().getNumber() + " " + i.m().getTitle() + " " + i.m().getClientName() + " " + i.bill().getInvoiceNumber()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingLong(Item::daysOverdue).reversed().thenComparing(Item::outstanding, Comparator.reverseOrder()))
                .map(LawCollectionService::row).toList();
        BigDecimal total = open.stream().map(Item::outstanding).reduce(ZERO, BigDecimal::add);
        List<Item> late = open.stream().filter(i -> i.daysOverdue() > 0).toList();
        BigDecimal overdue = late.stream().map(Item::outstanding).reduce(ZERO, BigDecimal::add);
        Set<Long> clients = open.stream().map(i -> i.m().getClientCustomerId()).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return new CollectionsDto(rows, total, overdue, late.size(), clients.size(), aging(open), canSend(a, canBill), mailService.isConfigured(), lawService.settingsEntity().getDefaultTermDays());
    }

    // ------------------------------------------------------------------ the client's statement

    @Transactional(readOnly = true)
    public FeeStatementData statementData(Actor a, Long customerId, LocalDate from, LocalDate to) {
        access.on();
        if (from != null && to != null && to.isBefore(from)) throw ApiException.badRequest(I18n.t("The end date is before the start date."));
        LocalDate today = LocalDate.now();
        List<Item> all = items(m -> access.canView(m, a) && customerId.equals(m.getClientCustomerId()), today);
        Customer c = customerRepository.findById(customerId).orElseThrow(() -> ApiException.notFound(I18n.t("Client not found.")));
        boolean sees = matterRepository.findByClientCustomerIdOrderByOpenedDateDescIdDesc(customerId).stream().anyMatch(m -> access.canView(m, a));
        if (!sees) throw ApiException.notFound(I18n.t("Client not found."));
        List<Item> shown = all.stream().filter(i -> (from == null || !i.date().isBefore(from)) && (to == null || !i.date().isAfter(to)))
                .sorted(Comparator.comparing(Item::date).thenComparing(i -> i.bill().getInvoiceNumber() == null ? "" : i.bill().getInvoiceNumber())).toList();
        List<FeeLine> lines = shown.stream().map(i -> new FeeLine(i.date(), i.bill().getInvoiceNumber(), i.m().getNumber() + " " + i.m().getTitle(), i.total(), i.paid(), i.outstanding(), i.due(),
                i.daysOverdue())).toList();
        BigDecimal billed = shown.stream().map(Item::total).reduce(ZERO, BigDecimal::add);
        BigDecimal paid = shown.stream().map(Item::paid).reduce(ZERO, BigDecimal::add);
        BigDecimal owed = shown.stream().map(Item::outstanding).reduce(ZERO, BigDecimal::add);
        return new FeeStatementData(c.getName(), c.getEmail(), c.getPhone(), today, from, to, lines, billed, paid, owed, aging(shown.stream().filter(Item::open).toList()));
    }

    @Transactional(readOnly = true)
    public Printed statement(Actor a, Long customerId, LocalDate from, LocalDate to) {
        FeeStatementData d = statementData(a, customerId, from, to);
        return new Printed(files.feeStatement(d), "fee-statement-" + slug(d.clientName()) + ".pdf");
    }

    private static String slug(String s) {
        return s == null ? "client" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private void requireSender(Actor a, boolean canBill) {
        access.on();
        if (!canSend(a, canBill)) throw ApiException.forbidden(I18n.t("Only a billing user, a manager or an admin can send fee e-mails."));
        if (!mailService.isConfigured()) throw ApiException.badRequest(I18n.t("E-mail is not set up yet. Set up SMTP in Settings > Email first."));
    }

    @Transactional
    public MailedClient emailStatement(Actor a, boolean canBill, Long customerId) {
        requireSender(a, canBill);
        FeeStatementData d = statementData(a, customerId, null, null);
        if (d.clientEmail() == null || d.clientEmail().isBlank()) throw ApiException.badRequest(I18n.t("This client has no e-mail address on the customer record."));
        Printed pdf = new Printed(files.feeStatement(d), "fee-statement-" + slug(d.clientName()) + ".pdf");
        String business = files.businessName();
        StringBuilder body = new StringBuilder(I18n.t("<p>Dear ")).append(HtmlUtils.htmlEscape(d.clientName())).append(",</p>");
        body.append(I18n.t("<p>Please find attached the statement of your fees with ")).append(HtmlUtils.htmlEscape(business)).append(I18n.t(" up to ")).append(LawPrintFiles.day(d.asOf())).append(".</p>");
        if (d.totalOutstanding().signum() > 0) body.append(I18n.t("<p>The amount still to pay is <b>")).append(LawPrintFiles.amount(files.currency(), d.totalOutstanding())).append("</b>.</p>");
        contact(body);
        mailService.send(d.clientEmail().trim(), I18n.t("Your statement of fees - ") + business, body.toString(), List.of(new EmailAttachment(pdf.fileName(), "application/pdf", pdf.content())));
        auditLogService.record("LAW_FEE_STATEMENT_SENT", "E-mailed the fee statement of " + d.clientName() + " to " + d.clientEmail(), "Customer", customerId);
        return new MailedClient(d.clientEmail().trim(), I18n.t("Statement sent to ") + d.clientEmail().trim() + ".");
    }

    private void contact(StringBuilder body) {
        String contact = files.contactLine();
        if (!contact.isEmpty()) body.append(I18n.t("<p>Questions? Contact us: ")).append(HtmlUtils.htmlEscape(contact)).append("</p>");
        body.append("<p>").append(HtmlUtils.htmlEscape(files.businessName())).append("</p>");
    }

    // ------------------------------------------------------------------ reminders

    private String reminderHtml(String clientName, List<Item> list) {
        String cur = files.currency();
        BigDecimal total = list.stream().map(Item::outstanding).reduce(ZERO, BigDecimal::add);
        StringBuilder b = new StringBuilder(I18n.t("<p>Dear ")).append(HtmlUtils.htmlEscape(clientName)).append(",</p>");
        b.append(I18n.t("<p>Our records show that the following fees are overdue. If you have already paid, please ignore this reminder and accept our thanks.</p>"));
        b.append(I18n.t("<table cellpadding=\"4\" style=\"border-collapse:collapse;\" border=\"1\"><tr><th>Invoice</th><th>Matter</th><th>Due</th><th>Days overdue</th><th align=\"right\">Still to pay</th></tr>"));
        for (Item i : list) {
            b.append("<tr><td>").append(HtmlUtils.htmlEscape(String.valueOf(i.bill().getInvoiceNumber()))).append("</td><td>").append(HtmlUtils.htmlEscape(i.m().getNumber() + " " + i.m().getTitle()))
                    .append("</td><td>").append(i.due()).append("</td><td>").append(i.daysOverdue()).append("</td><td align=\"right\">").append(LawPrintFiles.amount(cur, i.outstanding())).append("</td></tr>");
        }
        b.append(I18n.t("<tr><td colspan=\"4\"><b>Total</b></td><td align=\"right\"><b>")).append(LawPrintFiles.amount(cur, total)).append("</b></td></tr></table>");
        b.append(I18n.t("<p>A statement of your fees is attached. Please arrange payment as soon as you can, or contact us if there is a problem with any invoice.</p>"));
        contact(b);
        return b.toString();
    }

    /** Sends one reminder mail per client for the given overdue items, with the client's statement attached; remembers the reminder on each invoice. */
    private RemindResult sendGroups(Map<Long, List<Item>> groups, Actor a) {
        int mails = 0, invoices = 0, skipped = 0;
        List<ReminderItem> items = new ArrayList<>();
        for (Map.Entry<Long, List<Item>> e : groups.entrySet()) {
            List<Item> list = e.getValue();
            Item first = list.get(0);
            String name = first.m().getClientName();
            String email = first.customer() != null && first.customer().getEmail() != null && !first.customer().getEmail().isBlank() ? first.customer().getEmail().trim() : null;
            BigDecimal owed = list.stream().map(Item::outstanding).reduce(ZERO, BigDecimal::add);
            if (email == null) {
                skipped++;
                items.add(new ReminderItem(e.getKey(), name, null, list.size(), owed, I18n.t("This client has no e-mail address.")));
                continue;
            }
            try {
                FeeStatementData d = a != null ? statementData(a, e.getKey(), null, null) : systemStatement(e.getKey());
                byte[] pdf = files.feeStatement(d);
                mailService.send(email, I18n.t("Overdue fees - ") + files.businessName(), reminderHtml(name, list), List.of(new EmailAttachment("fee-statement-" + slug(name) + ".pdf", "application/pdf", pdf)));
            } catch (Exception ex) {
                log.warn("Could not send the fee reminder to {}: {}", name, ex.getMessage());
                skipped++;
                items.add(new ReminderItem(e.getKey(), name, email, list.size(), owed, I18n.t("The e-mail could not be sent.")));
                continue;
            }
            Instant now = Instant.now();
            for (Item i : list) {
                LawBill b = i.bill();
                b.setReminderCount(b.getReminderCount() + 1);
                b.setLastRemindedAt(now);
                billRepository.save(b);
            }
            auditLogService.record("LAW_FEE_REMINDER_SENT", "Fee reminder for " + list.size() + " invoice(s) (" + LawPrintFiles.money(owed) + ") sent to " + name + " at " + email, "Customer", e.getKey());
            mails++;
            invoices += list.size();
            items.add(new ReminderItem(e.getKey(), name, email, list.size(), owed, null));
        }
        return new RemindResult(mails, invoices, skipped, items);
    }

    /** The statement for the automatic run: all of the client's invoices, whoever may see them. */
    private FeeStatementData systemStatement(Long customerId) {
        LocalDate today = LocalDate.now();
        List<Item> all = items(m -> customerId.equals(m.getClientCustomerId()), today).stream().sorted(Comparator.comparing(Item::date)).toList();
        Customer c = customerRepository.findById(customerId).orElseThrow(() -> ApiException.notFound(I18n.t("Client not found.")));
        List<FeeLine> lines = all.stream().map(i -> new FeeLine(i.date(), i.bill().getInvoiceNumber(), i.m().getNumber() + " " + i.m().getTitle(), i.total(), i.paid(), i.outstanding(), i.due(), i.daysOverdue())).toList();
        return new FeeStatementData(c.getName(), c.getEmail(), c.getPhone(), today, null, null, lines, all.stream().map(Item::total).reduce(ZERO, BigDecimal::add),
                all.stream().map(Item::paid).reduce(ZERO, BigDecimal::add), all.stream().map(Item::outstanding).reduce(ZERO, BigDecimal::add), aging(all.stream().filter(Item::open).toList()));
    }

    private static Map<Long, List<Item>> byClient(List<Item> list) {
        Map<Long, List<Item>> out = new LinkedHashMap<>();
        for (Item i : list) if (i.m().getClientCustomerId() != null) out.computeIfAbsent(i.m().getClientCustomerId(), k -> new ArrayList<>()).add(i);
        return out;
    }

    /** Reminds the clients of the chosen overdue invoices now, whatever the automatic settings say. */
    @Transactional
    public RemindResult remind(Actor a, boolean canBill, RemindRequest r) {
        requireSender(a, canBill);
        if (r == null || r.billIds() == null || r.billIds().isEmpty()) throw ApiException.badRequest(I18n.t("Choose the invoices to remind about."));
        Set<Long> want = Set.copyOf(r.billIds());
        List<Item> chosen = items(m -> access.canView(m, a), LocalDate.now()).stream().filter(i -> want.contains(i.bill().getId()) && i.open() && i.daysOverdue() > 0).toList();
        if (chosen.isEmpty()) throw ApiException.badRequest(I18n.t("None of those invoices is overdue."));
        return sendGroups(byClient(chosen), a);
    }

    private List<Item> eligible(LocalDate today) {
        LawSettings s = lawService.settingsEntity();
        Instant cutoff = Instant.now().minus(s.getCollectionsRepeatDays(), ChronoUnit.DAYS);
        return items(m -> true, today).stream().filter(i -> i.open() && i.daysOverdue() >= s.getCollectionsFirstDays() && i.bill().getReminderCount() < s.getCollectionsMax()
                && (i.bill().getLastRemindedAt() == null || !i.bill().getLastRemindedAt().isAfter(cutoff))).toList();
    }

    /** Who the automatic run would remind right now. */
    @Transactional(readOnly = true)
    public List<ReminderItem> preview() {
        access.on();
        List<ReminderItem> out = new ArrayList<>();
        for (Map.Entry<Long, List<Item>> e : byClient(eligible(LocalDate.now())).entrySet()) {
            Item f = e.getValue().get(0);
            String email = f.customer() != null && f.customer().getEmail() != null && !f.customer().getEmail().isBlank() ? f.customer().getEmail().trim() : null;
            out.add(new ReminderItem(e.getKey(), f.m().getClientName(), email, e.getValue().size(), e.getValue().stream().map(Item::outstanding).reduce(ZERO, BigDecimal::add),
                    email == null ? I18n.t("This client has no e-mail address.") : null));
        }
        return out;
    }

    /** The "send now" button: the automatic run, started by hand. */
    @Transactional
    public RemindResult runNow(Actor a, boolean canBill) {
        requireSender(a, canBill);
        return sendGroups(byClient(eligible(LocalDate.now())), null);
    }

    @Scheduled(cron = "0 35 6 * * *")
    public void daily() {
        try {
            if (!moduleService.isEnabled(ModuleKey.LAW_FIRM)) return;
            if (!lawService.settingsEntity().isCollectionsEnabled() || !mailService.isConfigured()) return;
            RemindResult r = runDaily();
            if (r.mails() > 0) log.info("Law fee reminders: {} mails for {} invoices", r.mails(), r.invoices());
        } catch (Exception e) {
            log.warn("Law fee reminders skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public RemindResult runDaily() {
        return sendGroups(byClient(eligible(LocalDate.now())), null);
    }
}
