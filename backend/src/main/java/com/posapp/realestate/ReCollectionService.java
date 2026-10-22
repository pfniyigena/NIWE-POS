package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.installment.InstallmentPlanStatus;
import com.posapp.installment.InstallmentService;
import com.posapp.installment.dto.InstallmentDtos.InstallmentDto;
import com.posapp.installment.dto.InstallmentDtos.PlanSummaryDto;
import com.posapp.installment.dto.InstallmentDtos.ReminderResultDto;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.RePrintService.Printed;
import com.posapp.realestate.dto.RealEstateCollectionDtos.CollectionBoard;
import com.posapp.realestate.dto.RealEstateCollectionDtos.CollectionRow;
import com.posapp.realestate.dto.RealEstateCollectionDtos.MailedBuyer;
import com.posapp.realestate.dto.RealEstateCollectionDtos.SaleNoteDto;
import com.posapp.realestate.dto.RealEstateCollectionDtos.SaleNoteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collecting what buyers owe on sold units: the board (overdue, due soon, no schedule), the notes kept on each sale (calls, promises to pay), the reminder to the buyer
 * (the Installments module's own e-mail) and the buyer statement by e-mail.
 */
@Service
public class ReCollectionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<SaleNoteKind> BY_HAND = EnumSet.of(SaleNoteKind.CALL, SaleNoteKind.PROMISE, SaleNoteKind.NOTE);

    private final RealEstateAccess access;
    private final ReUnitSaleRepository saleRepository;
    private final ReUnitRepository unitRepository;
    private final ReProjectRepository projectRepository;
    private final ReSaleNoteRepository noteRepository;
    private final InstallmentService installmentService;
    private final InvoiceService invoiceService;
    private final ModuleService moduleService;
    private final MailService mailService;
    private final CustomerRepository customerRepository;
    private final ReSettingsService settingsService;
    private final RePrintService printService;
    private final RePrintFiles files;
    private final AuditLogService auditLogService;

    public ReCollectionService(RealEstateAccess access, ReUnitSaleRepository saleRepository, ReUnitRepository unitRepository, ReProjectRepository projectRepository,
                               ReSaleNoteRepository noteRepository, InstallmentService installmentService, InvoiceService invoiceService, ModuleService moduleService, MailService mailService,
                               CustomerRepository customerRepository, ReSettingsService settingsService, RePrintService printService, RePrintFiles files, AuditLogService auditLogService) {
        this.access = access;
        this.saleRepository = saleRepository;
        this.unitRepository = unitRepository;
        this.projectRepository = projectRepository;
        this.noteRepository = noteRepository;
        this.installmentService = installmentService;
        this.invoiceService = invoiceService;
        this.moduleService = moduleService;
        this.mailService = mailService;
        this.customerRepository = customerRepository;
        this.settingsService = settingsService;
        this.printService = printService;
        this.files = files;
        this.auditLogService = auditLogService;
    }

    private ReUnitSale find(Long id) {
        return saleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Sale not found: " + id));
    }

    private InvoiceDto invoice(Long id) {
        if (id == null) return null;
        try {
            return invoiceService.getById(id);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    // ------------------------------------------------------------------ the board

    /** Every sold unit with something to collect, in three lists: overdue (worst first), due within {@code days} days (soonest first) and balances with no schedule. */
    @Transactional
    public CollectionBoard board(Long projectId, Long agentId, Integer days) {
        access.on();
        LocalDate today = LocalDate.now();
        int soon = days == null ? 14 : Math.max(1, Math.min(90, days));
        List<ReUnitSale> sold = saleRepository.findByStageIn(EnumSet.of(UnitSaleStage.SOLD)).stream()
                .filter(s -> (projectId == null || projectId.equals(s.getProjectId())) && (agentId == null || agentId.equals(s.getAgentUserId()))).toList();
        Map<Long, PlanSummaryDto> plans = sold.isEmpty() ? Map.of()
                : installmentService.list(InstallmentPlanStatus.ACTIVE, null).stream().collect(Collectors.toMap(PlanSummaryDto::invoiceId, p -> p, (x, y) -> x));
        Map<Long, ReUnit> units = unitRepository.findAll().stream().collect(Collectors.toMap(ReUnit::getId, u -> u));
        Map<Long, ReProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(ReProject::getId, p -> p));
        Map<Long, List<ReSaleNote>> notes = sold.isEmpty() ? Map.of()
                : noteRepository.findBySaleIdIn(sold.stream().map(ReUnitSale::getId).toList()).stream().collect(Collectors.groupingBy(ReSaleNote::getSaleId));
        List<CollectionRow> overdue = new ArrayList<>();
        List<CollectionRow> dueSoon = new ArrayList<>();
        List<CollectionRow> unscheduled = new ArrayList<>();
        for (ReUnitSale s : sold) {
            InvoiceDto inv = invoice(s.getInvoiceId());
            if (inv == null || "VOID".equals(inv.status())) continue;
            BigDecimal paid = inv.amountPaid() == null ? ZERO : inv.amountPaid();
            BigDecimal out = inv.amountOutstanding() == null ? ZERO : inv.amountOutstanding();
            if (out.signum() <= 0) continue;
            PlanSummaryDto plan = plans.get(s.getInvoiceId());
            ReUnit u = units.get(s.getUnitId());
            ReProject p = projects.get(s.getProjectId());
            List<ReSaleNote> mine = notes.getOrDefault(s.getId(), List.of());
            ReSaleNote last = mine.stream().max(Comparator.comparing(ReSaleNote::getId)).orElse(null);
            ReSaleNote promise = mine.stream().filter(n -> n.getKind() == SaleNoteKind.PROMISE && n.getPromiseDate() != null).max(Comparator.comparing(ReSaleNote::getId)).orElse(null);
            if (plan == null) {
                unscheduled.add(row(s, u, p, "INVOICE", s.getAgreedPrice(), paid, out, ZERO, 0, 0, null, null, null, last, promise, today));
                continue;
            }
            boolean late = plan.overdueAmount() != null && plan.overdueAmount().signum() > 0;
            if (late) {
                long oldest = installmentService.get(plan.id()).installments().stream().filter(i -> i.daysOverdue() > 0 && i.balance().signum() > 0)
                        .mapToLong(InstallmentDto::daysOverdue).max().orElse(0);
                overdue.add(row(s, u, p, "PLAN", s.getAgreedPrice(), paid, out, plan.overdueAmount(), plan.overdueInstallments(), oldest, plan.nextDueDate(), plan.nextDueAmount(), plan.id(), last, promise, today));
            } else if (plan.nextDueDate() != null && !plan.nextDueDate().isAfter(today.plusDays(soon))) {
                dueSoon.add(row(s, u, p, "PLAN", s.getAgreedPrice(), paid, out, ZERO, 0, 0, plan.nextDueDate(), plan.nextDueAmount(), plan.id(), last, promise, today));
            }
        }
        overdue.sort(Comparator.comparingLong(CollectionRow::oldestDaysOverdue).reversed().thenComparing(CollectionRow::overdueAmount, Comparator.reverseOrder()));
        dueSoon.sort(Comparator.comparing(CollectionRow::nextDueDate));
        unscheduled.sort(Comparator.comparing(CollectionRow::outstanding, Comparator.reverseOrder()));
        return new CollectionBoard(today, soon, sum(overdue, true), sum(dueSoon, false), sum(unscheduled, false), moduleService.isEnabled(ModuleKey.INSTALLMENTS), mailService.isConfigured(),
                overdue, dueSoon, unscheduled);
    }

    private static BigDecimal sum(List<CollectionRow> rows, boolean overdue) {
        return rows.stream().map(r -> overdue ? r.overdueAmount() : r.nextDueAmount() != null ? r.nextDueAmount() : r.outstanding()).reduce(ZERO, BigDecimal::add);
    }

    private CollectionRow row(ReUnitSale s, ReUnit u, ReProject p, String basis, BigDecimal price, BigDecimal paid, BigDecimal out, BigDecimal overdueAmount, int overdueCount, long oldest,
                              LocalDate nextDue, BigDecimal nextAmount, Long planId, ReSaleNote last, ReSaleNote promise, LocalDate today) {
        LocalDate pd = promise == null ? null : promise.getPromiseDate();
        boolean broken = pd != null && pd.isBefore(today) && overdueAmount.signum() > 0;
        return new CollectionRow(s.getId(), s.getCode(), s.getProjectId(), p == null ? null : p.getName(), u == null ? null : u.getUnitNo(), s.getBuyerName(), s.getBuyerPhone(), s.getBuyerEmail(),
                s.getAgentUserId(), s.getAgentName(), basis, price, paid, out, overdueAmount, overdueCount, oldest, nextDue, nextAmount, planId, last == null ? null : last.getKind(),
                last == null ? null : last.getText(), last == null ? null : last.getCreatedAt(), pd, broken);
    }

    // ------------------------------------------------------------------ notes

    @Transactional(readOnly = true)
    public List<SaleNoteDto> notes(Long saleId) {
        access.on();
        find(saleId);
        return noteRepository.findBySaleIdOrderByIdDesc(saleId).stream().map(n -> new SaleNoteDto(n.getId(), n.getKind(), n.getText(), n.getPromiseDate(), n.getCreatedByName(), n.getCreatedAt())).toList();
    }

    private ReSaleNote save(Long saleId, SaleNoteKind kind, String text, LocalDate promise, Actor a) {
        ReSaleNote n = new ReSaleNote();
        n.setSaleId(saleId);
        n.setKind(kind);
        n.setText(text.length() > 2000 ? text.substring(0, 2000) : text);
        n.setPromiseDate(promise);
        if (a != null) {
            n.setCreatedByUserId(a.user().getId());
            n.setCreatedByName(RealEstateAccess.name(a.user()));
        }
        return noteRepository.save(n);
    }

    @Transactional
    public SaleNoteDto addNote(Actor a, Long saleId, SaleNoteRequest r) {
        access.on();
        ReUnitSale s = find(saleId);
        if (s.getStage() != UnitSaleStage.SOLD) throw ApiException.conflict("Collection notes are kept on sold units that still owe money.");
        if (r == null || r.kind() == null || !BY_HAND.contains(r.kind())) throw ApiException.badRequest("Choose what this is: a call, a promise to pay, or a note.");
        String text = r.text() == null ? "" : r.text().trim();
        if (text.isEmpty()) throw ApiException.badRequest("Write what was said.");
        if (text.length() > 2000) throw ApiException.badRequest("A note can be at most 2000 characters.");
        LocalDate promise = null;
        if (r.kind() == SaleNoteKind.PROMISE) {
            promise = r.promiseDate();
            if (promise == null) throw ApiException.badRequest("Choose the day the buyer promised to pay by.");
            if (promise.isBefore(LocalDate.now())) throw ApiException.badRequest("The promised day cannot be in the past.");
            if (promise.isAfter(LocalDate.now().plusYears(1))) throw ApiException.badRequest("The promised day is more than a year away.");
        }
        ReSaleNote n = save(saleId, r.kind(), text, promise, a);
        auditLogService.record("RE_SALE_NOTE", "Collection note on sale " + s.getCode() + ": " + r.kind(), "ReUnitSale", saleId);
        return new SaleNoteDto(n.getId(), n.getKind(), n.getText(), n.getPromiseDate(), n.getCreatedByName(), n.getCreatedAt());
    }

    // ------------------------------------------------------------------ e-mail to the buyer

    private String buyerEmail(ReUnitSale s) {
        if (s.getBuyerEmail() != null && !s.getBuyerEmail().isBlank()) return s.getBuyerEmail().trim();
        Customer c = s.getBuyerCustomerId() == null ? null : customerRepository.findById(s.getBuyerCustomerId()).orElse(null);
        return c != null && c.getEmail() != null && !c.getEmail().isBlank() ? c.getEmail().trim() : null;
    }

    private void requireMail() {
        if (!mailService.isConfigured()) throw ApiException.badRequest("E-mail is not set up yet. Set up SMTP in Settings > Email first.");
    }

    /** E-mails the buyer what is overdue or due soon, using the Installments module's reminder. Needs an active payment plan. */
    @Transactional
    public MailedBuyer remind(Actor a, Long saleId) {
        access.on();
        ReUnitSale s = find(saleId);
        if (s.getStage() != UnitSaleStage.SOLD) throw ApiException.conflict("Only a sold unit that still owes money can be reminded.");
        requireMail();
        PlanSummaryDto plan = s.getInvoiceId() == null ? null
                : installmentService.forInvoice(s.getInvoiceId()).stream().filter(p -> p.status() == InstallmentPlanStatus.ACTIVE).findFirst().orElse(null);
        if (plan == null) throw ApiException.conflict("This sale has no payment plan, so no instalment is due to remind about. E-mail the statement instead.");
        ReminderResultDto r = installmentService.remindNow(plan.id());
        save(saleId, SaleNoteKind.REMINDER, "Reminder e-mailed to " + r.sentTo(), null, a);
        return new MailedBuyer(r.sentTo(), "Reminder sent to " + r.sentTo() + ".");
    }

    /** E-mails the buyer's statement (PDF) and records it on the sale. */
    @Transactional
    public MailedBuyer emailStatement(Actor a, Long saleId) {
        access.on();
        ReUnitSale s = find(saleId);
        requireMail();
        String to = buyerEmail(s);
        if (to == null) throw ApiException.badRequest("This buyer has no e-mail address on the sale or the customer record.");
        Printed pdf = printService.statement(a, saleId);
        ReSettings rs = settingsService.entity();
        String business = files.businessName();
        StringBuilder body = new StringBuilder();
        body.append("<p>Dear ").append(HtmlUtils.htmlEscape(s.getBuyerName())).append(",</p>");
        body.append("<p>Please find attached your statement for sale <b>").append(HtmlUtils.htmlEscape(s.getCode())).append("</b>, with the payments received and the schedule still to pay.</p>");
        String contact = contact(rs);
        if (!contact.isEmpty()) body.append("<p>Questions? Contact us: ").append(HtmlUtils.htmlEscape(contact)).append("</p>");
        body.append("<p>").append(HtmlUtils.htmlEscape(business)).append("</p>");
        mailService.send(to, "Your statement for " + s.getCode() + " - " + business, body.toString(), List.of(new EmailAttachment(pdf.fileName(), "application/pdf", pdf.content())));
        save(saleId, SaleNoteKind.STATEMENT, "Statement e-mailed to " + to, null, a);
        auditLogService.record("RE_SALE_STATEMENT", "E-mailed the statement of sale " + s.getCode() + " to " + to, "ReUnitSale", saleId);
        return new MailedBuyer(to, "Statement sent to " + to + ".");
    }

    private static String contact(ReSettings s) {
        StringBuilder b = new StringBuilder();
        for (String part : new String[]{s.getWebsitePhone(), s.getWebsiteEmail()}) if (part != null && !part.isBlank()) b.append(b.length() == 0 ? "" : "  |  ").append(part.trim());
        return b.toString();
    }
}
