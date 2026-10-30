package com.posapp.installment;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.credit.dto.RecordInvoicePaymentRequest;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.installment.InstallmentCalculator.Line;
import com.posapp.installment.dto.InstallmentDtos.*;
import com.posapp.installment.dto.InstallmentRequests.*;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.reports.ReportExportService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import com.posapp.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Customer instalment plans on invoices. The plan holds the schedule; what has been paid is worked out, oldest instalment first,
 * from (a) what the invoice has received since the plan was made — principal — and (b) the plan's own payments' interest/fee part.
 * So a payment recorded on the invoice screen and one recorded from the plan both move the schedule the same way.
 */
@Service
public class InstallmentService {

    private static final Logger log = LoggerFactory.getLogger(InstallmentService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final java.util.Set<String> ZERO_DECIMAL_CURRENCIES = java.util.Set.of("RWF", "XAF", "XOF", "UGX", "JPY", "KRW", "BIF", "CLP", "DJF", "GNF", "KMF", "PYG", "VND", "VUV", "XPF");

    private final InstallmentPlanRepository planRepository;
    private final InstallmentPaymentRepository paymentRepository;
    private final InstallmentSettingsRepository settingsRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final CustomerCreditService customerCreditService;
    private final AccountingService accountingService;
    private final MailService mailService;
    private final StoreSettingsRepository storeSettingsRepository;
    private final ReportExportService exportService;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;
    private final ModuleService moduleService;
    private final InstallmentFollowUpRepository followUpRepository;
    private final InstallmentPrintFiles printFiles;

    public InstallmentService(InstallmentPlanRepository planRepository, InstallmentPaymentRepository paymentRepository,
                              InstallmentSettingsRepository settingsRepository, InvoiceRepository invoiceRepository,
                              CustomerRepository customerRepository, CustomerCreditService customerCreditService,
                              AccountingService accountingService, MailService mailService, StoreSettingsRepository storeSettingsRepository,
                              ReportExportService exportService, BranchAccessService branchAccessService, AuditLogService auditLogService,
                              ModuleService moduleService, InstallmentFollowUpRepository followUpRepository, InstallmentPrintFiles printFiles) {
        this.planRepository = planRepository;
        this.paymentRepository = paymentRepository;
        this.settingsRepository = settingsRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.customerCreditService = customerCreditService;
        this.accountingService = accountingService;
        this.mailService = mailService;
        this.storeSettingsRepository = storeSettingsRepository;
        this.exportService = exportService;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
        this.moduleService = moduleService;
        this.followUpRepository = followUpRepository;
        this.printFiles = printFiles;
    }

    // ---- settings ----

    @Transactional
    public InstallmentSettings settingsEntity() {
        return settingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            InstallmentSettings fresh = new InstallmentSettings();
            StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
            if (store != null && store.getCurrencyCode() != null && ZERO_DECIMAL_CURRENCIES.contains(store.getCurrencyCode().trim().toUpperCase())) {
                fresh.setDefaultWholeUnits(true);   // no cents in this currency: start new plans with whole amounts
            }
            return settingsRepository.save(fresh);
        });
    }

    @Transactional
    public SettingsDto settings() {
        return toDto(settingsEntity());
    }

    @Transactional
    public SettingsDto updateSettings(SettingsRequest r) {
        InstallmentSettings s = settingsEntity();
        if (r.remindersEnabled() != null) {
            s.setRemindersEnabled(r.remindersEnabled());
        }
        if (r.reminderDaysBefore() != null) {
            s.setReminderDaysBefore(inRange(r.reminderDaysBefore(), 0, 60, "Days before a due date"));
        }
        if (r.overdueReminderEveryDays() != null) {
            s.setOverdueReminderEveryDays(inRange(r.overdueReminderEveryDays(), 1, 90, "Days between overdue reminders"));
        }
        if (r.defaultWholeUnits() != null) {
            s.setDefaultWholeUnits(r.defaultWholeUnits());
        }
        if (r.defaultInterestRate() != null) {
            s.setDefaultInterestRate(percent(r.defaultInterestRate(), "Interest rate"));
        }
        if (r.defaultGraceDays() != null) {
            s.setDefaultGraceDays(inRange(r.defaultGraceDays(), 0, 90, "Grace days"));
        }
        if (r.defaultLateFeeFixed() != null) {
            s.setDefaultLateFeeFixed(nonNegative(r.defaultLateFeeFixed(), "Late fee"));
        }
        if (r.defaultLateFeePercent() != null) {
            s.setDefaultLateFeePercent(percent(r.defaultLateFeePercent(), "Late fee percentage"));
        }
        if (r.payoffRebatePercent() != null) {
            s.setPayoffRebatePercent(percent(r.payoffRebatePercent(), "Early payoff interest waiver"));
        }
        if (r.defaultAfterDays() != null) {
            s.setDefaultAfterDays(inRange(r.defaultAfterDays(), 0, 730, "Days overdue before a plan is marked defaulted"));
        }
        if (r.agreementTerms() != null) {
            String t = r.agreementTerms().trim();
            if (t.length() > 4000) {
                throw ApiException.badRequest("The agreement wording is limited to 4000 characters.");
            }
            s.setAgreementTerms(t.isEmpty() ? null : t);
        }
        settingsRepository.save(s);
        auditLogService.record("INSTALLMENT_SETTINGS_UPDATED", "Updated the instalment settings");
        return toDto(s);
    }

    private static SettingsDto toDto(InstallmentSettings s) {
        return new SettingsDto(s.isRemindersEnabled(), s.getReminderDaysBefore(), s.getOverdueReminderEveryDays(), s.isDefaultWholeUnits(), s.getDefaultInterestRate(),
                s.getDefaultGraceDays(), s.getDefaultLateFeeFixed(), s.getDefaultLateFeePercent(), s.getPayoffRebatePercent(), s.getDefaultAfterDays(), s.getAgreementTerms());
    }

    /** A plan that still has money to collect: active or defaulted. */
    private static boolean isOpen(InstallmentPlan p) {
        return p.getStatus() == InstallmentPlanStatus.ACTIVE || p.getStatus() == InstallmentPlanStatus.DEFAULTED;
    }

    private List<InstallmentPlan> openPlans() {
        List<InstallmentPlan> out = new ArrayList<>(planRepository.findByStatus(InstallmentPlanStatus.ACTIVE));
        out.addAll(planRepository.findByStatus(InstallmentPlanStatus.DEFAULTED));
        return out;
    }

    // ---- reading ----

    @Transactional
    public List<PlanSummaryDto> list(InstallmentPlanStatus status, Long customerId) {
        LocalDate today = today();
        refreshActive(today);
        return planRepository.findAllByOrderByIdDesc().stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> customerId == null || customerId.equals(p.getCustomerId()))
                .filter(p -> branchAccessService.canAccess(p.getBranchId()))
                .map(p -> summary(p, view(p, today), today)).toList();
    }

    @Transactional
    public List<PlanSummaryDto> forInvoice(Long invoiceId) {
        LocalDate today = today();
        refreshActive(today);
        return planRepository.findByInvoiceIdOrderByIdDesc(invoiceId).stream()
                .filter(p -> branchAccessService.canAccess(p.getBranchId()))
                .map(p -> summary(p, view(p, today), today)).toList();
    }

    @Transactional
    public PlanDetailDto get(Long id) {
        InstallmentPlan p = entity(id);
        branchAccessService.assertCanAccess(p.getBranchId());
        syncWithInvoice(p, today());
        return detail(p);
    }

    private PlanDetailDto detail(InstallmentPlan p) {
        LocalDate today = today();
        List<Row> rows = view(p, today);
        List<InstallmentDto> lines = rows.stream().map(r -> new InstallmentDto(r.i.getSeq(), r.i.getDueDate(), r.i.getPrincipalDue(),
                r.i.getInterestDue(), r.i.getLateFee(), r.i.isLateFeeWaived(), r.total(), r.paid(), r.left(), r.state, r.daysOverdue, r.principalLeft)).toList();
        List<PaymentDto> payments = paymentRepository.findByPlanIdOrderByPaidAtDescIdDesc(p.getId()).stream()
                .map(x -> new PaymentDto(x.getId(), x.getPaidAt(), x.getAmount(), x.getPrincipalPart(), x.getChargesPart(), x.getMethod(),
                        x.getReceiptNumber(), x.getNote(), x.getRecordedByName(), x.isReversed(), x.getReversedAt(), x.getReversedByName(), x.getReverseReason())).toList();
        TermsDto terms = new TermsDto(p.getInvoiceTotal(), p.getDeposit(), p.getInterestMethod(), p.getInterestRate(), p.getFrequency(),
                p.getFirstDueDate(), p.getGraceDays(), p.getLateFeeFixed(), p.getLateFeePercent(), p.isRemindersEnabled(), p.isWholeUnits(), p.getNotes(),
                p.getAdjustmentNote(), p.getCreatedByName(), p.getCancelledOn(), p.getCancelReason(), p.getDefaultedOn(), p.getDefaultReason(),
                p.getWrittenOffOn(), p.getWrittenOffAmount(), p.getWriteOffReason());
        return new PlanDetailDto(summary(p, rows, today), terms, lines, payments);
    }

    /** The headline numbers for the dashboard: only active plans, only branches the caller may see. */
    @Transactional
    public SummaryDto summary() {
        LocalDate today = today();
        refreshActive(today);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        long active = 0;
        long overdueCount = 0;
        BigDecimal outstanding = ZERO;
        BigDecimal overdue = ZERO;
        BigDecimal dueThisMonth = ZERO;
        long defaulted = 0;
        BigDecimal defaultedBalance = ZERO;
        long broken = 0;
        for (InstallmentPlan p : openPlans()) {
            if (!branchAccessService.canAccess(p.getBranchId())) {
                continue;
            }
            if (p.getStatus() == InstallmentPlanStatus.DEFAULTED) {
                defaulted++;
                defaultedBalance = defaultedBalance.add(view(p, today).stream().map(Row::left).reduce(ZERO, BigDecimal::add));
                continue;   // a defaulted plan is shown on its own line, not in the active figures
            }
            active++;
            List<Row> planRows = view(p, today);
            if (planRows.stream().anyMatch(r -> r.state == InstallmentState.OVERDUE) && promiseBroken(p, planRows, today)) {
                broken++;
            }
            for (Row r : planRows) {
                BigDecimal left = r.left();
                if (left.signum() <= 0) {
                    continue;
                }
                outstanding = outstanding.add(left);
                if (r.state == InstallmentState.OVERDUE) {
                    overdue = overdue.add(left);
                    overdueCount++;
                }
                if (!r.i.getDueDate().isBefore(monthStart) && !r.i.getDueDate().isAfter(monthEnd)) {
                    dueThisMonth = dueThisMonth.add(left);
                }
            }
        }
        Instant from = monthStart.atStartOfDay(zone()).toInstant();
        Instant to = monthEnd.plusDays(1).atStartOfDay(zone()).toInstant();
        BigDecimal collected = ZERO;
        for (InstallmentPayment x : paymentRepository.findByPaidAtBetweenOrderByPaidAtDescIdDesc(from, to)) {
            if (!x.isReversed() && branchAccessService.canAccess(x.getPlan().getBranchId())) {
                collected = collected.add(x.getAmount());
            }
        }
        return new SummaryDto(active, outstanding, overdue, overdueCount, dueThisMonth, collected, defaulted, defaultedBalance, broken);
    }

    /** Every instalment of every plan as flat lines (the reports filter and group them). */
    @Transactional
    public List<InstallmentLine> lines(boolean activeOnly, Long branchId) {
        LocalDate today = today();
        refreshActive(today);
        List<InstallmentLine> out = new ArrayList<>();
        for (InstallmentPlan p : planRepository.findAllByOrderByIdDesc()) {
            if ((activeOnly && !isOpen(p)) || !branchAccessService.canAccess(p.getBranchId())
                    || (branchId != null && !branchId.equals(p.getBranchId()))) {
                continue;
            }
            for (Row r : view(p, today)) {
                out.add(new InstallmentLine(p.getId(), p.getPlanNumber(), p.getBranchId(), p.getCustomerName(), p.getInvoiceNumber(), r.i.getSeq(),
                        p.getInstallments().size(), r.i.getDueDate(), r.i.getPrincipalDue(), r.i.getInterestDue(), r.i.getLateFee(), r.total(),
                        r.paid(), r.left(), r.state, r.daysOverdue));
            }
        }
        return out;
    }

    /** Payments received through plans in a period (reports). */
    @Transactional(readOnly = true)
    public List<InstallmentPayment> paymentsBetween(Instant from, Instant to, Long branchId) {
        return paymentRepository.findByPaidAtBetweenOrderByPaidAtDescIdDesc(from, to).stream()
                .filter(x -> !x.isReversed())
                .filter(x -> branchAccessService.canAccess(x.getPlan().getBranchId()))
                .filter(x -> branchId == null || branchId.equals(x.getPlan().getBranchId())).toList();
    }

    // ---- creating ----

    /** Works out the schedule without saving anything. */
    @Transactional
    public PreviewDto preview(PlanRequest r) {
        Invoice invoice = payableInvoice(r.invoiceId());
        return build(invoice, r, false).preview;
    }

    @Transactional
    public PlanDetailDto create(PlanRequest r, AppUser caller) {
        Invoice invoice = payableInvoice(r.invoiceId());
        if (planRepository.existsByInvoiceIdAndStatus(invoice.getId(), InstallmentPlanStatus.ACTIVE)
                || planRepository.existsByInvoiceIdAndStatus(invoice.getId(), InstallmentPlanStatus.DEFAULTED)) {
            throw ApiException.conflict("Invoice " + invoice.getInvoiceNumber() + " already has an open instalment plan.");
        }
        Built b = build(invoice, r, true);
        BigDecimal deposit = b.deposit;
        if (deposit.signum() > 0) {
            // The deposit is an ordinary payment on the invoice, so its receipt and accounting are the usual ones.
            RecordInvoicePaymentRequest pay = new RecordInvoicePaymentRequest();
            pay.setAmount(deposit);
            pay.setMethod(r.depositMethod());
            pay.setNote("Deposit for an instalment plan");
            customerCreditService.recordInvoicePayment(invoice.getId(), pay, caller);
        }
        InstallmentPlan p = new InstallmentPlan();
        p.setPlanNumber("IP-" + System.currentTimeMillis());
        p.setInvoiceId(invoice.getId());
        p.setInvoiceNumber(invoice.getInvoiceNumber());
        p.setCustomerId(invoice.getCustomerId());
        p.setCustomerName(invoice.getCustomerName());
        p.setBranchId(invoice.getBranchId());
        p.setInvoiceTotal(invoice.getTotalAmount());
        p.setDeposit(deposit);
        p.setFinancedAmount(b.preview.financedAmount());
        p.setBaselinePaid(customerCreditService.amountPaidForInvoice(invoice));
        p.setInterestMethod(b.method);
        p.setInterestRate(b.rate);
        p.setTotalInterest(b.preview.totalInterest());
        p.setFrequency(b.frequency);
        p.setNumberOfInstallments(b.lines.size());
        p.setFirstDueDate(b.lines.get(0).dueDate());
        p.setGraceDays(b.graceDays);
        p.setLateFeeFixed(b.feeFixed);
        p.setLateFeePercent(b.feePercent);
        p.setRemindersEnabled(b.reminders);
        p.setWholeUnits(b.wholeUnits);
        p.setNotes(blankToNull(r.notes()));
        p.setCreatedByName(caller != null ? caller.getFullName() : null);
        for (Line l : b.lines) {
            Installment i = new Installment();
            i.setPlan(p);
            i.setSeq(l.seq());
            i.setDueDate(l.dueDate());
            i.setPrincipalDue(l.principal());
            i.setInterestDue(l.interest());
            p.getInstallments().add(i);
        }
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_PLAN_CREATED", "Created instalment plan " + p.getPlanNumber() + " for invoice " + invoice.getInvoiceNumber()
                + " (" + p.getNumberOfInstallments() + " instalments)", "InstallmentPlan", p.getId());
        return detail(p);
    }

    private record Built(PreviewDto preview, List<Line> lines, BigDecimal deposit, InterestMethod method, BigDecimal rate,
                         InstallmentFrequency frequency, int graceDays, BigDecimal feeFixed, BigDecimal feePercent, boolean reminders, boolean wholeUnits) {
    }

    private Built build(Invoice invoice, PlanRequest r, boolean forCreate) {
        BigDecimal outstanding = customerCreditService.outstandingForInvoice(invoice);
        BigDecimal paid = customerCreditService.amountPaidForInvoice(invoice);
        BigDecimal deposit = r.deposit() == null ? ZERO : InstallmentCalculator.round(nonNegative(r.deposit(), "The deposit"));
        if (deposit.compareTo(outstanding) >= 0) {
            throw ApiException.badRequest("The deposit must be less than what is still unpaid on the invoice (" + outstanding.toPlainString() + ").");
        }
        if (forCreate && deposit.signum() > 0 && (r.depositMethod() == null || r.depositMethod().isBlank())) {
            throw ApiException.badRequest("Choose how the deposit is paid.");
        }
        BigDecimal financed = outstanding.subtract(deposit);
        int n = r.numberOfInstallments() == null ? 0 : r.numberOfInstallments();
        if (n < 2 || n > 60) {
            throw ApiException.badRequest("Choose between 2 and 60 instalments.");
        }
        InstallmentFrequency frequency = r.frequency() == null ? InstallmentFrequency.MONTHLY : r.frequency();
        if (r.firstDueDate() == null) {
            throw ApiException.badRequest("Choose the date the first instalment falls due.");
        }
        if (r.firstDueDate().isBefore(today())) {
            throw ApiException.badRequest("The first instalment can't fall due in the past.");
        }
        InterestMethod method = r.interestMethod() == null ? InterestMethod.NONE : r.interestMethod();
        BigDecimal rate = r.interestRate() == null ? ZERO : percent(r.interestRate(), "The interest rate");
        if (rate.signum() == 0) {
            method = InterestMethod.NONE;
        }
        int grace = r.graceDays() == null ? 0 : inRange(r.graceDays(), 0, 90, "Grace days");
        BigDecimal feeFixed = r.lateFeeFixed() == null ? ZERO : nonNegative(r.lateFeeFixed(), "The late fee");
        BigDecimal feePercent = r.lateFeePercent() == null ? ZERO : percent(r.lateFeePercent(), "The late fee percentage");
        boolean reminders = r.remindersEnabled() == null || r.remindersEnabled();

        boolean whole = r.wholeUnits() != null ? r.wholeUnits() : settingsEntity().isDefaultWholeUnits();
        List<Line> lines = InstallmentCalculator.build(financed, method, rate, frequency, n, r.firstDueDate(), whole ? 0 : 2);
        BigDecimal interest = lines.stream().map(Line::interest).reduce(ZERO, BigDecimal::add);
        List<PreviewLineDto> preview = lines.stream().map(l -> new PreviewLineDto(l.seq(), l.dueDate(), l.principal(), l.interest(),
                l.principal().add(l.interest()))).toList();
        PreviewDto dto = new PreviewDto(invoice.getTotalAmount(), paid, outstanding, deposit, financed, interest, financed.add(interest), preview);
        return new Built(dto, lines, deposit, method, rate, frequency, grace, feeFixed, feePercent, reminders, whole);
    }

    private Invoice payableInvoice(Long invoiceId) {
        if (invoiceId == null) {
            throw ApiException.badRequest("Choose an invoice.");
        }
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        branchAccessService.assertCanAccess(invoice.getBranchId());
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.badRequest("Only an issued invoice can be paid in instalments — " + invoice.getInvoiceNumber() + " is " + invoice.getStatus() + ".");
        }
        if (invoice.getCustomerId() == null) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber() + " has no customer, so it can't have an instalment plan.");
        }
        if (customerCreditService.outstandingForInvoice(invoice).signum() <= 0) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber() + " is already fully paid.");
        }
        return invoice;
    }

    // ---- paying ----

    @Transactional
    public PlanDetailDto recordPayment(Long planId, PaymentRequest r, AppUser caller) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (isOpen(p) && syncWithInvoice(p, today())) {
            throw ApiException.conflict("The invoice is no longer payable, so this plan was cancelled: " + p.getCancelReason());
        }
        if (!isOpen(p)) {
            throw ApiException.conflict("This plan is " + p.getStatus().name().toLowerCase().replace('_', ' ') + " — payments can't be recorded on it.");
        }
        if (r.amount() == null || r.amount().signum() <= 0) {
            throw ApiException.badRequest("Enter the amount received.");
        }
        if (r.method() == null || r.method().isBlank()) {
            throw ApiException.badRequest("Choose the payment method.");
        }
        BigDecimal amount = InstallmentCalculator.round(r.amount());
        applyPayment(p, amount, r.method(), r.note(), caller, today());
        return detail(p);
    }

    /** Allocates a payment oldest-first (charges before principal inside an instalment), records it on the invoice and in the books. */
    private InstallmentPayment applyPayment(InstallmentPlan p, BigDecimal amount, String method, String note, AppUser caller, LocalDate today) {
        assessLateFees(p, today);
        List<Row> rows = view(p, today);
        BigDecimal totalLeft = rows.stream().map(Row::left).reduce(ZERO, BigDecimal::add);
        if (amount.compareTo(totalLeft) > 0) {
            throw ApiException.badRequest("That is more than the " + totalLeft.toPlainString() + " still owed on this plan.");
        }
        // Oldest first; within an instalment the interest and fees are settled before the principal.
        BigDecimal remaining = amount;
        BigDecimal principalPart = ZERO;
        BigDecimal chargesPart = ZERO;
        for (Row row : rows) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal c = remaining.min(row.chargesLeft);
            chargesPart = chargesPart.add(c);
            remaining = remaining.subtract(c);
            BigDecimal pr = remaining.min(row.principalLeft);
            principalPart = principalPart.add(pr);
            remaining = remaining.subtract(pr);
        }

        InstallmentPayment pay = new InstallmentPayment();
        pay.setPlan(p);
        pay.setAmount(amount);
        pay.setPrincipalPart(principalPart);
        pay.setChargesPart(chargesPart);
        pay.setMethod(method);
        pay.setNote(clip(blankToNull(note), 250));
        pay.setRecordedByName(caller != null ? caller.getFullName() : null);
        if (principalPart.signum() > 0) {
            RecordInvoicePaymentRequest inv = new RecordInvoicePaymentRequest();
            inv.setAmount(principalPart);
            inv.setMethod(method);
            inv.setNote(clip("Instalment plan " + p.getPlanNumber() + (note != null && !note.isBlank() ? " — " + note.trim() : ""), 250));
            CustomerPaymentDto done = customerCreditService.recordInvoicePayment(p.getInvoiceId(), inv, caller);
            pay.setCustomerPaymentId(done.id());
            pay.setReceiptNumber(done.receiptNumber());
        } else {
            pay.setReceiptNumber("IPR-" + System.currentTimeMillis());
        }
        paymentRepository.save(pay);
        if (chargesPart.signum() > 0) {
            accountingService.postInstallmentCharges(pay.getId(), "Interest and fees on instalment plan " + p.getPlanNumber(), chargesPart, p.getBranchId());
        }
        refreshStatus(p, today);
        auditLogService.record("INSTALLMENT_PAYMENT", "Recorded " + amount.toPlainString() + " on instalment plan " + p.getPlanNumber(),
                "InstallmentPlan", p.getId());
        return pay;
    }

    @Transactional
    public PlanDetailDto cancel(Long planId, String reason) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (!isOpen(p)) {
            throw ApiException.conflict("Only an active or defaulted plan can be cancelled.");
        }
        markCancelled(p, blankToNull(reason));
        auditLogService.record("INSTALLMENT_PLAN_CANCELLED", "Cancelled instalment plan " + p.getPlanNumber(), "InstallmentPlan", p.getId());
        return detail(p);
    }

    private void markCancelled(InstallmentPlan p, String reason) {
        p.setStatus(InstallmentPlanStatus.CANCELLED);
        p.setCancelledOn(today());
        p.setCancelReason(reason);
        planRepository.save(p);
    }

    // ---- keeping a plan in step with its invoice ----

    private void refreshActive(LocalDate today) {
        for (InstallmentPlan p : openPlans()) {
            try {
                syncWithInvoice(p, today);
            } catch (RuntimeException e) {
                log.warn("Instalment plan {} could not be checked against its invoice: {}", p.getPlanNumber(), e.getMessage());
            }
        }
    }

    /**
     * If the invoice is no longer a payable invoice (voided, or cancelled and credited) the plan is cancelled. If it has been reduced
     * (a return or credit note) the unpaid principal is trimmed to match, taking it off the last instalments first. Returns true when
     * the plan was cancelled.
     */
    private boolean syncWithInvoice(InstallmentPlan p, LocalDate today) {
        if (!isOpen(p)) {
            return false;
        }
        Invoice inv = invoiceRepository.findById(p.getInvoiceId()).orElse(null);
        if (inv == null || inv.getStatus() != InvoiceStatus.ISSUED) {
            markCancelled(p, "Invoice " + p.getInvoiceNumber() + " is " + (inv == null ? "gone" : inv.getStatus().name().toLowerCase()) + " — cancelled automatically.");
            auditLogService.record("INSTALLMENT_PLAN_CANCELLED", "Instalment plan " + p.getPlanNumber() + " was cancelled automatically: its invoice is no longer payable", "InstallmentPlan", p.getId());
            return true;
        }
        List<Row> rows = view(p, today);
        BigDecimal remaining = rows.stream().map(r -> r.principalLeft).reduce(ZERO, BigDecimal::add);
        BigDecimal diff = remaining.subtract(customerCreditService.outstandingForInvoice(inv));
        if (diff.signum() <= 0) {
            return false;
        }
        BigDecimal total = diff;
        for (int k = rows.size() - 1; k >= 0 && diff.signum() > 0; k--) {
            Row r = rows.get(k);
            BigDecimal take = diff.min(r.principalLeft);
            if (take.signum() <= 0) {
                continue;
            }
            r.i.setPrincipalDue(r.i.getPrincipalDue().subtract(take));
            if (r.i.getPrincipalDue().signum() == 0 && r.paid().signum() == 0) {
                r.i.setInterestDue(ZERO);   // nothing left to finance in this instalment, so no interest on it either
            }
            diff = diff.subtract(take);
        }
        addNote(p, today + ": the invoice was reduced by " + money(total) + " (a return or credit), so the last instalments were reduced to match.");
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_PLAN_ADJUSTED", "Instalment plan " + p.getPlanNumber() + " reduced by " + money(total) + " to match its invoice", "InstallmentPlan", p.getId());
        return false;
    }

    private static void addNote(InstallmentPlan p, String text) {
        p.setAdjustmentNote(p.getAdjustmentNote() == null || p.getAdjustmentNote().isBlank() ? text : p.getAdjustmentNote() + "\n" + text);
    }

    // ---- editing the schedule ----

    /**
     * Replaces every instalment that isn't fully paid yet — different dates, different amounts, more or fewer instalments. What is
     * already paid stays as it is. The principal across the new instalments must add up to what was still scheduled (nothing owed is
     * created or forgotten here) and interest and fees can't be lowered below what has already been paid.
     */
    @Transactional
    public PlanDetailDto reschedule(Long planId, RescheduleRequest r, AppUser caller) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        LocalDate today = today();
        if (!isOpen(p)) {
            throw ApiException.conflict("Only an active or defaulted plan can be rescheduled.");
        }
        if (syncWithInvoice(p, today)) {
            throw ApiException.conflict("The invoice is no longer payable, so this plan was cancelled: " + p.getCancelReason());
        }
        List<Row> rows = view(p, today);
        int firstOpen = 0;
        while (firstOpen < rows.size() && rows.get(firstOpen).state == InstallmentState.PAID) {
            firstOpen++;
        }
        if (firstOpen == rows.size()) {
            throw ApiException.badRequest("Every instalment is already paid.");
        }
        List<Row> kept = rows.subList(0, firstOpen);
        List<Row> open = rows.subList(firstOpen, rows.size());
        List<RescheduleLine> lines = r == null || r.installments() == null ? List.of() : r.installments();
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Add at least one instalment.");
        }
        if (firstOpen + lines.size() > 60) {
            throw ApiException.badRequest("A plan can have at most 60 instalments.");
        }
        LocalDate prev = kept.isEmpty() ? null : kept.get(kept.size() - 1).i.getDueDate();
        BigDecimal newPrincipal = ZERO;
        BigDecimal newInterest = ZERO;
        for (RescheduleLine l : lines) {
            if (l.dueDate() == null) {
                throw ApiException.badRequest("Every instalment needs a due date.");
            }
            if (l.principalDue() == null || l.interestDue() == null || l.principalDue().signum() < 0 || l.interestDue().signum() < 0) {
                throw ApiException.badRequest("Amounts can't be negative.");
            }
            if (prev != null && l.dueDate().isBefore(prev)) {
                throw ApiException.badRequest("The due dates must be in order, each on or after the one before.");
            }
            prev = l.dueDate();
            newPrincipal = newPrincipal.add(InstallmentCalculator.round(l.principalDue()));
            newInterest = newInterest.add(InstallmentCalculator.round(l.interestDue()));
        }
        BigDecimal oldPrincipal = open.stream().map(x -> x.i.getPrincipalDue()).reduce(ZERO, BigDecimal::add);
        if (newPrincipal.compareTo(oldPrincipal) != 0) {
            throw ApiException.badRequest("The principal of the instalments must add up to " + oldPrincipal.toPlainString() + " — it adds up to " + newPrincipal.toPlainString() + ".");
        }
        BigDecimal chargesPaid = rows.stream().map(x -> x.chargesPaid).reduce(ZERO, BigDecimal::add);
        BigDecimal newCharges = kept.stream().map(x -> x.i.getInterestDue().add(x.i.getLateFee())).reduce(ZERO, BigDecimal::add).add(newInterest);
        for (int k = 0; k < Math.min(lines.size(), open.size()); k++) {
            newCharges = newCharges.add(open.get(k).i.getLateFee());   // late fees stay with the instalment they were charged on
        }
        if (newCharges.compareTo(chargesPaid) < 0) {
            throw ApiException.badRequest("Interest and fees can't be lowered below the " + chargesPaid.toPlainString() + " already paid.");
        }

        for (int k = 0; k < lines.size(); k++) {
            RescheduleLine l = lines.get(k);
            Installment i;
            if (k < open.size()) {
                i = open.get(k).i;
                if (!i.getDueDate().equals(l.dueDate())) {
                    i.setReminderSentOn(null);
                    i.setLastOverdueReminderOn(null);
                }
            } else {
                i = new Installment();
                i.setPlan(p);
                i.setSeq(firstOpen + k + 1);
                p.getInstallments().add(i);
            }
            i.setDueDate(l.dueDate());
            i.setPrincipalDue(InstallmentCalculator.round(l.principalDue()));
            i.setInterestDue(InstallmentCalculator.round(l.interestDue()));
        }
        for (int k = lines.size(); k < open.size(); k++) {
            p.getInstallments().remove(open.get(k).i);
        }
        p.setNumberOfInstallments(firstOpen + lines.size());
        p.setTotalInterest(p.getInstallments().stream().map(Installment::getInterestDue).reduce(ZERO, BigDecimal::add));
        addNote(p, today + ": the schedule was edited" + (caller != null && caller.getFullName() != null ? " by " + caller.getFullName() : "")
                + " (" + lines.size() + " unpaid instalment(s) now).");
        if (p.getStatus() == InstallmentPlanStatus.DEFAULTED) {
            p.setStatus(InstallmentPlanStatus.ACTIVE);   // a new arrangement was agreed: the plan is back in good standing
            p.setDefaultedOn(null);
            p.setDefaultReason(null);
            addNote(p, today + ": the plan was taken out of default with the new schedule.");
        }
        planRepository.save(p);
        refreshStatus(p, today);
        auditLogService.record("INSTALLMENT_RESCHEDULED", "Edited the schedule of instalment plan " + p.getPlanNumber(), "InstallmentPlan", p.getId());
        return detail(p);
    }

    /** Removes the late fee from one instalment (and stops it being charged again). */
    @Transactional
    public PlanDetailDto waiveLateFee(Long planId, int seq) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        Installment i = p.getInstallments().stream().filter(x -> x.getSeq() == seq).findFirst()
                .orElseThrow(() -> ApiException.notFound("Instalment " + seq + " not found"));
        i.setLateFee(ZERO);
        i.setLateFeeWaived(true);
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_FEE_WAIVED", "Waived the late fee on instalment " + seq + " of " + p.getPlanNumber(), "InstallmentPlan", p.getId());
        return detail(p);
    }

    // ---- late fees ----

    /** Adds the late fee, once, to every instalment that is still unpaid after its grace period. */
    private void assessLateFees(InstallmentPlan p, LocalDate today) {
        if (p.getStatus() != InstallmentPlanStatus.ACTIVE || (p.getLateFeeFixed().signum() <= 0 && p.getLateFeePercent().signum() <= 0)) {
            return;
        }
        for (Row r : view(p, today)) {
            Installment i = r.i;
            if (i.isLateFeeWaived() || i.getLateFeeAssessedOn() != null || r.left().signum() <= 0
                    || !i.getDueDate().plusDays(p.getGraceDays()).isBefore(today)) {
                continue;
            }
            BigDecimal unpaid = r.principalLeft.add(r.interestLeft());
            BigDecimal fee = InstallmentCalculator.round(p.getLateFeeFixed().add(
                    unpaid.multiply(p.getLateFeePercent()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)), p.isWholeUnits() ? 0 : 2);
            i.setLateFeeAssessedOn(today);
            i.setLateFee(fee);
        }
        planRepository.save(p);
    }

    /** Runs every night: late fees, and closing plans that have been settled in full. */
    @Scheduled(cron = "0 30 5 * * *")
    @Transactional
    public void scheduledNightly() {
        nightlyBody();
    }

    /** "Run the nightly check now" (also used by the tests): returns how many plans went into default or were completed by this run. */
    @Transactional
    public NightlyResultDto runNightlyNow() {
        long defaultedBefore = planRepository.findByStatus(InstallmentPlanStatus.DEFAULTED).size();
        long completedBefore = planRepository.findByStatus(InstallmentPlanStatus.COMPLETED).size();
        nightlyBody();
        return new NightlyResultDto((int) Math.max(0, planRepository.findByStatus(InstallmentPlanStatus.DEFAULTED).size() - defaultedBefore),
                (int) Math.max(0, planRepository.findByStatus(InstallmentPlanStatus.COMPLETED).size() - completedBefore));
    }

    private void nightlyBody() {
        if (!moduleService.isEnabled(ModuleKey.INSTALLMENTS)) {
            return;
        }
        LocalDate today = today();
        int defaultAfter = settingsEntity().getDefaultAfterDays();
        for (InstallmentPlan p : openPlans()) {
            try {
                if (syncWithInvoice(p, today)) {
                    continue;
                }
                assessLateFees(p, today);
                refreshStatus(p, today);
                if (defaultAfter > 0 && p.getStatus() == InstallmentPlanStatus.ACTIVE) {
                    long oldest = view(p, today).stream().filter(r -> r.state == InstallmentState.OVERDUE).mapToLong(r -> r.daysOverdue).max().orElse(0);
                    if (oldest >= defaultAfter) {
                        markDefaulted(p, today, "Marked defaulted automatically: an instalment was " + oldest + " day(s) overdue.");
                        auditLogService.record("INSTALLMENT_PLAN_DEFAULTED", "Instalment plan " + p.getPlanNumber() + " was marked defaulted automatically (" + oldest + " days overdue)", "InstallmentPlan", p.getId());
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Instalment plan {} could not be updated: {}", p.getPlanNumber(), e.getMessage());
            }
        }
    }

    private void refreshStatus(InstallmentPlan p, LocalDate today) {
        if (isOpen(p) && view(p, today).stream().allMatch(r -> r.left().signum() <= 0)) {
            p.setStatus(InstallmentPlanStatus.COMPLETED);
            planRepository.save(p);
        }
    }

    // ---- reminders ----

    /** Every morning: one email per plan listing what is coming up or overdue. */
    @Scheduled(cron = "0 5 8 * * *")
    @Transactional
    public void dailyReminders() {
        if (!moduleService.isEnabled(ModuleKey.INSTALLMENTS) || !mailService.isConfigured()) {
            return;
        }
        InstallmentSettings s = settingsEntity();
        if (!s.isRemindersEnabled()) {
            return;
        }
        LocalDate today = today();
        for (InstallmentPlan p : planRepository.findByStatus(InstallmentPlanStatus.ACTIVE)) {
            if (!p.isRemindersEnabled()) {
                continue;
            }
            try {
                if (syncWithInvoice(p, today)) {
                    continue;   // its invoice is gone: no reminders
                }
                List<Row> due = new ArrayList<>();
                for (Row r : view(p, today)) {
                    Installment i = r.i;
                    if (r.left().signum() <= 0) {
                        continue;
                    }
                    long days = ChronoUnit.DAYS.between(today, i.getDueDate());
                    boolean upcoming = days >= 0 && days <= s.getReminderDaysBefore() && i.getReminderSentOn() == null;
                    boolean overdue = days < 0 && (i.getLastOverdueReminderOn() == null
                            || ChronoUnit.DAYS.between(i.getLastOverdueReminderOn(), today) >= s.getOverdueReminderEveryDays());
                    if (upcoming || overdue) {
                        due.add(r);
                    }
                }
                if (due.isEmpty()) {
                    continue;
                }
                if (sendEmail(p, due, today) != null) {
                    for (Row r : due) {
                        if (r.state == InstallmentState.OVERDUE) {
                            r.i.setLastOverdueReminderOn(today);
                        } else {
                            r.i.setReminderSentOn(today);
                        }
                    }
                    planRepository.save(p);
                }
            } catch (RuntimeException e) {
                log.warn("Reminder for instalment plan {} failed: {}", p.getPlanNumber(), e.getMessage());
            }
        }
    }

    /** "Send reminder" button: emails the customer what is overdue or due soon, now. */
    @Transactional
    public ReminderResultDto remindNow(Long planId) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (p.getStatus() != InstallmentPlanStatus.ACTIVE) {
            throw ApiException.conflict("Only an active plan can be reminded about.");
        }
        LocalDate today = today();
        if (syncWithInvoice(p, today)) {
            throw ApiException.conflict("The invoice is no longer payable, so this plan was cancelled.");
        }
        int ahead = settingsEntity().getReminderDaysBefore();
        List<Row> due = view(p, today).stream()
                .filter(r -> r.left().signum() > 0 && !r.i.getDueDate().isAfter(today.plusDays(ahead))).toList();
        if (due.isEmpty()) {
            throw ApiException.badRequest("Nothing is overdue or due soon on this plan.");
        }
        String to = sendEmail(p, due, today);
        if (to == null) {
            throw ApiException.badRequest("This customer has no email address.");
        }
        for (Row r : due) {
            if (r.state == InstallmentState.OVERDUE) {
                r.i.setLastOverdueReminderOn(today);
            } else {
                r.i.setReminderSentOn(today);
            }
        }
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_REMINDER", "Sent an instalment reminder for " + p.getPlanNumber() + " to " + to, "InstallmentPlan", p.getId());
        logEmailContact(p, "Reminder e-mailed to " + to, null);
        return new ReminderResultDto(true, to);
    }

    /** Sends the reminder; returns the address used, or null when the customer has no email. */
    private String sendEmail(InstallmentPlan p, List<Row> rows, LocalDate today) {
        Customer customer = p.getCustomerId() == null ? null : customerRepository.findById(p.getCustomerId()).orElse(null);
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return null;
        }
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        String cur = store != null && store.getCurrencyCode() != null ? store.getCurrencyCode() + " " : "";
        String business = store != null && store.getBusinessName() != null ? store.getBusinessName() : "";
        boolean anyOverdue = rows.stream().anyMatch(r -> r.state == InstallmentState.OVERDUE);
        StringBuilder b = new StringBuilder();
        b.append("<p>Dear ").append(esc(customer.getName())).append(",</p>");
        b.append("<p>").append(anyOverdue ? "This is a reminder that the instalment(s) below on invoice <b>" : "A friendly reminder of the instalment(s) coming due on invoice <b>")
                .append(esc(p.getInvoiceNumber())).append("</b> ").append(anyOverdue ? "are overdue:" : "are due soon:").append("</p>");
        b.append("<table cellpadding=\"6\" style=\"border-collapse:collapse;border:1px solid #ccc;\"><tr><th align=\"left\">No.</th><th align=\"left\">Due date</th><th align=\"right\">Amount</th><th align=\"left\"></th></tr>");
        BigDecimal total = ZERO;
        for (Row r : rows) {
            total = total.add(r.left());
            b.append("<tr><td>").append(r.i.getSeq()).append(" of ").append(p.getInstallments().size()).append("</td><td>").append(r.i.getDueDate())
                    .append("</td><td align=\"right\">").append(esc(cur)).append(money(r.left())).append("</td><td>")
                    .append(r.state == InstallmentState.OVERDUE ? "<span style=\"color:#b00020;\">overdue " + r.daysOverdue + " day(s)</span>" : "").append("</td></tr>");
        }
        b.append("</table><p>Total: <b>").append(esc(cur)).append(money(total)).append("</b></p>");
        b.append("<p>Please arrange payment. If you have already paid, thank you — please disregard this message.</p><p>").append(esc(business)).append("</p>");
        mailService.send(customer.getEmail(), (anyOverdue ? "Overdue instalment" : "Instalment due") + " — " + p.getInvoiceNumber(), b.toString(), List.of());
        return customer.getEmail();
    }

    // ---- schedule PDF ----

    @Transactional
    public byte[] schedulePdf(Long planId) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        List<List<String>> rows = new ArrayList<>();
        for (Row r : view(p, today())) {
            rows.add(List.of(String.valueOf(r.i.getSeq()), String.valueOf(r.i.getDueDate()), money(r.i.getPrincipalDue()), money(r.i.getInterestDue()),
                    money(r.i.getLateFee()), money(r.total()), money(r.paid()), money(r.left()), r.state.name()));
        }
        return exportService.toPdf("Instalment schedule " + p.getPlanNumber() + " — " + p.getCustomerName() + " (invoice " + p.getInvoiceNumber() + ")",
                List.of("No.", "Due date", "Principal", "Interest", "Late fee", "Total", "Paid", "Balance", "Status"), rows);
    }

    // ---- early payoff ----

    /** What it takes to settle the plan today. {@code rebatePercent} null = the setting's default. */
    @Transactional
    public PayoffQuoteDto payoffQuote(Long planId, BigDecimal rebatePercent) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        requireOpenForPayoff(p);
        return quote(p, rebatePercent != null ? percent(rebatePercent, "Interest waiver") : settingsEntity().getPayoffRebatePercent(), today());
    }

    private void requireOpenForPayoff(InstallmentPlan p) {
        if (isOpen(p) && syncWithInvoice(p, today())) {
            throw ApiException.conflict("The invoice is no longer payable, so this plan was cancelled: " + p.getCancelReason());
        }
        if (!isOpen(p)) {
            throw ApiException.conflict("This plan is " + p.getStatus().name().toLowerCase().replace('_', ' ') + " — it can't be paid off.");
        }
    }

    private PayoffQuoteDto quote(InstallmentPlan p, BigDecimal rebatePercent, LocalDate today) {
        assessLateFees(p, today);
        int scale = p.isWholeUnits() ? 0 : 2;
        BigDecimal principal = ZERO;
        BigDecimal interest = ZERO;
        BigDecimal fees = ZERO;
        BigDecimal rebate = ZERO;
        int left = 0;
        for (Row r : view(p, today)) {
            if (r.left().signum() <= 0) {
                continue;
            }
            left++;
            BigDecimal intLeft = r.interestLeft().min(r.chargesLeft);
            principal = principal.add(r.principalLeft);
            interest = interest.add(intLeft);
            fees = fees.add(r.chargesLeft.subtract(intLeft));
            if (r.i.getDueDate().isAfter(today)) {
                rebate = rebate.add(InstallmentCalculator.round(intLeft.multiply(rebatePercent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP), scale));
            }
        }
        BigDecimal settle = principal.add(interest).add(fees).subtract(rebate).max(ZERO);
        return new PayoffQuoteDto(today, principal, interest, fees, rebatePercent, rebate, settle, left);
    }

    /** Settles the plan in full today: the interest not yet earned on instalments not yet due is waived (by the chosen share), the rest is paid. */
    @Transactional
    public PlanDetailDto payoff(Long planId, PayoffRequest r, AppUser caller) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        requireOpenForPayoff(p);
        if (r.method() == null || r.method().isBlank()) {
            throw ApiException.badRequest("Choose the payment method.");
        }
        LocalDate today = today();
        BigDecimal pct = r.rebatePercent() != null ? percent(r.rebatePercent(), "Interest waiver") : settingsEntity().getPayoffRebatePercent();
        PayoffQuoteDto q = quote(p, pct, today);
        if (q.instalmentsLeft() == 0) {
            throw ApiException.badRequest("Nothing is left to pay on this plan.");
        }
        if (r.expectedAmount() != null && r.expectedAmount().compareTo(q.amountToSettle()) != 0) {
            throw ApiException.conflict("The payoff amount is now " + money(q.amountToSettle()) + " (it was " + money(r.expectedAmount()) + " on screen). Review it and confirm again.");
        }
        int scale = p.isWholeUnits() ? 0 : 2;
        if (q.rebate().signum() > 0) {
            for (Row row : view(p, today)) {
                if (row.left().signum() > 0 && row.i.getDueDate().isAfter(today)) {
                    BigDecimal intLeft = row.interestLeft().min(row.chargesLeft);
                    BigDecimal cut = InstallmentCalculator.round(intLeft.multiply(pct).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP), scale).min(intLeft);
                    row.i.setInterestDue(row.i.getInterestDue().subtract(cut));
                }
            }
            p.setTotalInterest(p.getInstallments().stream().map(Installment::getInterestDue).reduce(ZERO, BigDecimal::add));
            planRepository.save(p);
        }
        if (q.amountToSettle().signum() > 0) {
            applyPayment(p, q.amountToSettle(), r.method(), (r.note() == null || r.note().isBlank() ? "Paid off early" : "Paid off early — " + r.note().trim()), caller, today);
        } else {
            refreshStatus(p, today);
        }
        addNote(p, today + ": paid off early" + (q.rebate().signum() > 0 ? "; interest of " + money(q.rebate()) + " was waived." : "."));
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_PAID_OFF", "Instalment plan " + p.getPlanNumber() + " paid off early: " + money(q.amountToSettle()) + " received, interest waived " + money(q.rebate()),
                "InstallmentPlan", p.getId());
        return detail(p);
    }

    // ---- reversing a payment ----

    /** Takes a payment recorded through the plan back: the invoice payment is undone by a negative entry and the books get the mirror posting. */
    @Transactional
    public PlanDetailDto reversePayment(Long planId, Long paymentId, String reason, AppUser caller) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (p.getStatus() == InstallmentPlanStatus.WRITTEN_OFF) {
            throw ApiException.conflict("This plan was written off — its payments can't be reversed.");
        }
        InstallmentPayment pay = paymentRepository.findByIdAndPlanId(paymentId, planId).orElseThrow(() -> ApiException.notFound("Payment not found on this plan"));
        if (pay.isReversed()) {
            throw ApiException.conflict("This payment has already been reversed.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Say why the payment is being reversed.");
        }
        if (pay.getCustomerPaymentId() != null) {
            com.posapp.credit.CustomerPayment rev = customerCreditService.reverseInvoicePayment(pay.getCustomerPaymentId(), reason, caller);
            pay.setReversalPaymentId(rev.getId());
        }
        if (pay.getChargesPart().signum() > 0) {
            accountingService.postInstallmentChargesReversal(pay.getId(), "Reversal of interest and fees on instalment plan " + p.getPlanNumber(), pay.getChargesPart(), p.getBranchId());
        }
        pay.setReversedAt(Instant.now());
        pay.setReversedByName(caller != null ? caller.getFullName() : null);
        pay.setReverseReason(clip(reason.trim(), 250));
        paymentRepository.save(pay);
        LocalDate today = today();
        if (p.getStatus() == InstallmentPlanStatus.COMPLETED && view(p, today).stream().anyMatch(row -> row.left().signum() > 0)) {
            p.setStatus(InstallmentPlanStatus.ACTIVE);   // money is owing again
        }
        addNote(p, today + ": payment " + pay.getReceiptNumber() + " of " + money(pay.getAmount()) + " was reversed (" + reason.trim() + ").");
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_PAYMENT_REVERSED", "Reversed payment " + pay.getReceiptNumber() + " of " + money(pay.getAmount()) + " on instalment plan " + p.getPlanNumber()
                + " — " + reason.trim(), "InstallmentPlan", p.getId());
        return detail(p);
    }

    // ---- default and write-off ----

    @Transactional
    public PlanDetailDto markDefaulted(Long planId, String reason) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (p.getStatus() != InstallmentPlanStatus.ACTIVE) {
            throw ApiException.conflict("Only an active plan can be marked defaulted.");
        }
        markDefaulted(p, today(), blankToNull(reason) != null ? reason.trim() : "Marked defaulted by hand.");
        auditLogService.record("INSTALLMENT_PLAN_DEFAULTED", "Marked instalment plan " + p.getPlanNumber() + " as defaulted", "InstallmentPlan", p.getId());
        return detail(p);
    }

    private void markDefaulted(InstallmentPlan p, LocalDate today, String reason) {
        p.setStatus(InstallmentPlanStatus.DEFAULTED);
        p.setDefaultedOn(today);
        p.setDefaultReason(clip(reason, 250));
        planRepository.save(p);
    }

    @Transactional
    public PlanDetailDto restoreFromDefault(Long planId) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (p.getStatus() != InstallmentPlanStatus.DEFAULTED) {
            throw ApiException.conflict("Only a defaulted plan can be put back to active.");
        }
        p.setStatus(InstallmentPlanStatus.ACTIVE);
        p.setDefaultedOn(null);
        p.setDefaultReason(null);
        addNote(p, today() + ": taken out of default.");
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_PLAN_RESTORED", "Took instalment plan " + p.getPlanNumber() + " out of default", "InstallmentPlan", p.getId());
        return detail(p);
    }

    /** Writes the unpaid principal off as a bad debt (Dr bad debts / Cr accounts receivable). Unpaid interest and late fees were never booked and are dropped. */
    @Transactional
    public PlanDetailDto writeOff(Long planId, String reason, AppUser caller) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Say why the balance is being written off.");
        }
        requireOpenForPayoff(p);
        LocalDate today = today();
        BigDecimal principalLeft = view(p, today).stream().map(r -> r.principalLeft).reduce(ZERO, BigDecimal::add);
        Invoice inv = invoiceRepository.findById(p.getInvoiceId()).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        BigDecimal amount = principalLeft.min(customerCreditService.outstandingForInvoice(inv));
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("There is no unpaid principal to write off.");
        }
        customerCreditService.writeOffInvoice(p.getInvoiceId(), amount, clip("Instalment plan " + p.getPlanNumber() + " written off — " + reason.trim(), 250), caller);
        p.setStatus(InstallmentPlanStatus.WRITTEN_OFF);
        p.setWrittenOffOn(today);
        p.setWrittenOffAmount(amount);
        p.setWriteOffReason(clip(reason.trim(), 250));
        addNote(p, today + ": " + money(amount) + " written off as a bad debt (" + reason.trim() + ").");
        planRepository.save(p);
        auditLogService.record("INSTALLMENT_PLAN_WRITTEN_OFF", "Wrote off " + money(amount) + " on instalment plan " + p.getPlanNumber() + " — " + reason.trim(), "InstallmentPlan", p.getId());
        return detail(p);
    }

    // ---- collections log and worklist ----

    @Transactional(readOnly = true)
    public List<FollowUpDto> followUps(Long planId) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        return followUpDtos(planId);
    }

    private List<FollowUpDto> followUpDtos(Long planId) {
        return followUpRepository.findByPlanIdOrderByCreatedAtDescIdDesc(planId).stream()
                .map(f -> new FollowUpDto(f.getId(), f.getCreatedAt(), f.getKind(), f.getNote(), f.getPromisedOn(), f.getPromisedAmount(), f.getCreatedByName())).toList();
    }

    @Transactional
    public List<FollowUpDto> addFollowUp(Long planId, FollowUpRequest r, AppUser caller) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (r.kind() == null) {
            throw ApiException.badRequest("Choose what kind of contact this was.");
        }
        String note = blankToNull(r.note());
        if (note != null && note.length() > 1000) {
            throw ApiException.badRequest("Keep the note under 1000 characters.");
        }
        InstallmentFollowUp f = new InstallmentFollowUp();
        f.setPlan(p);
        f.setKind(r.kind());
        f.setNote(note);
        f.setCreatedByName(caller != null ? caller.getFullName() : null);
        if (r.kind() == InstallmentFollowUpKind.PROMISE) {
            LocalDate today = today();
            if (!isOpen(p)) {
                throw ApiException.conflict("A promise to pay can only be recorded on an active or defaulted plan.");
            }
            if (r.promisedOn() == null || r.promisedOn().isBefore(today) || r.promisedOn().isAfter(today.plusDays(365))) {
                throw ApiException.badRequest("Enter the date the customer promised to pay by (today or later, within a year).");
            }
            if (r.promisedAmount() != null && r.promisedAmount().signum() < 0) {
                throw ApiException.badRequest("The promised amount can't be negative.");
            }
            f.setPromisedOn(r.promisedOn());
            f.setPromisedAmount(r.promisedAmount() != null && r.promisedAmount().signum() > 0 ? r.promisedAmount() : null);
        } else if (note == null) {
            throw ApiException.badRequest("Write a short note about the contact.");
        }
        followUpRepository.save(f);
        return followUpDtos(planId);
    }

    private void logEmailContact(InstallmentPlan p, String text, AppUser caller) {
        InstallmentFollowUp f = new InstallmentFollowUp();
        f.setPlan(p);
        f.setKind(InstallmentFollowUpKind.EMAIL);
        f.setNote(text);
        f.setCreatedByName(caller != null ? caller.getFullName() : null);
        followUpRepository.save(f);
    }

    /** The latest promise to pay is broken when its date has passed, money is still overdue and nothing was received since it was made. */
    private boolean promiseBroken(InstallmentPlan p, List<Row> rows, LocalDate today) {
        if (rows.stream().noneMatch(r -> r.state == InstallmentState.OVERDUE)) {
            return false;
        }
        InstallmentFollowUp promise = followUpRepository.findByPlanIdOrderByCreatedAtDescIdDesc(p.getId()).stream()
                .filter(f -> f.getKind() == InstallmentFollowUpKind.PROMISE && f.getPromisedOn() != null).findFirst().orElse(null);
        if (promise == null || !promise.getPromisedOn().isBefore(today)) {
            return false;
        }
        return paymentRepository.findByPlanIdOrderByPaidAtDescIdDesc(p.getId()).stream().noneMatch(x -> !x.isReversed() && x.getPaidAt().isAfter(promise.getCreatedAt()));
    }

    /**
     * The "who to chase" list. filter: overdue (default), due-soon, promises (a promise still to come), broken (a missed promise), no-contact
     * (overdue and not contacted in the last 7 days), defaulted.
     */
    @Transactional
    public CollectionsDto collections(String filter, String search) {
        LocalDate today = today();
        refreshActive(today);
        String f = filter == null || filter.isBlank() ? "overdue" : filter.trim().toLowerCase();
        String q = search == null ? "" : search.trim().toLowerCase();
        java.util.Map<Long, Customer> customers = new java.util.HashMap<>();
        List<CollectionItemDto> items = new ArrayList<>();
        for (InstallmentPlan p : openPlans()) {
            if (!branchAccessService.canAccess(p.getBranchId())) {
                continue;
            }
            boolean defaulted = p.getStatus() == InstallmentPlanStatus.DEFAULTED;
            if ("defaulted".equals(f) != defaulted) {
                continue;   // defaulted plans have their own filter and stay out of the others
            }
            List<Row> rows = view(p, today);
            List<Row> overdueRows = rows.stream().filter(r -> r.state == InstallmentState.OVERDUE).toList();
            BigDecimal overdue = overdueRows.stream().map(Row::left).reduce(ZERO, BigDecimal::add);
            long oldest = overdueRows.stream().mapToLong(r -> r.daysOverdue).max().orElse(0);
            Row next = rows.stream().filter(r -> r.left().signum() > 0 && r.state != InstallmentState.OVERDUE).findFirst().orElse(null);
            List<InstallmentFollowUp> log = followUpRepository.findByPlanIdOrderByCreatedAtDescIdDesc(p.getId());
            InstallmentFollowUp last = log.isEmpty() ? null : log.get(0);
            InstallmentFollowUp promise = log.stream().filter(x -> x.getKind() == InstallmentFollowUpKind.PROMISE && x.getPromisedOn() != null).findFirst().orElse(null);
            boolean broken = promiseBroken(p, rows, today);
            boolean upcomingPromise = promise != null && !promise.getPromisedOn().isBefore(today);
            boolean recentlyContacted = last != null && last.getCreatedAt().isAfter(java.time.Instant.now().minus(7, ChronoUnit.DAYS));
            boolean soon = next != null && !next.i.getDueDate().isAfter(today.plusDays(7));
            boolean keep = switch (f) {
                case "due-soon" -> overdue.signum() == 0 && soon && !defaulted;
                case "promises" -> upcomingPromise && overdue.signum() > 0;
                case "broken" -> broken;
                case "no-contact" -> overdue.signum() > 0 && !recentlyContacted;
                case "defaulted" -> defaulted;
                default -> overdue.signum() > 0;
            };
            if (!keep) {
                continue;
            }
            Customer c = p.getCustomerId() == null ? null : customers.computeIfAbsent(p.getCustomerId(), id -> customerRepository.findById(id).orElse(null));
            if (!q.isEmpty() && !((p.getPlanNumber() + " " + nz(p.getCustomerName()) + " " + nz(p.getInvoiceNumber()) + " " + (c != null ? nz(c.getPhone()) : "")).toLowerCase().contains(q))) {
                continue;
            }
            String bucket = overdue.signum() == 0 ? "DUE_SOON" : oldest <= 30 ? "1-30" : oldest <= 60 ? "31-60" : oldest <= 90 ? "61-90" : "90+";
            items.add(new CollectionItemDto(p.getId(), p.getPlanNumber(), p.getCustomerId(), p.getCustomerName(), c != null ? c.getPhone() : null, c != null ? c.getEmail() : null,
                    p.getInvoiceNumber(), p.getStatus(), overdue, overdueRows.size(), oldest, bucket, next != null ? next.i.getDueDate() : null, next != null ? next.left() : null,
                    rows.stream().map(Row::left).reduce(ZERO, BigDecimal::add), last != null ? last.getCreatedAt() : null, last != null ? last.getKind() : null,
                    last != null ? last.getNote() : null, promise != null ? promise.getPromisedOn() : null, promise != null ? promise.getPromisedAmount() : null, broken));
        }
        items.sort(Comparator.comparingLong(CollectionItemDto::oldestOverdueDays).reversed().thenComparing(CollectionItemDto::overdueAmount, Comparator.reverseOrder()));
        BigDecimal total = items.stream().map(CollectionItemDto::overdueAmount).reduce(ZERO, BigDecimal::add);
        return new CollectionsDto(items, items.size(), total, items.stream().filter(CollectionItemDto::promiseBroken).count(),
                items.stream().filter(i -> today.equals(i.promisedOn())).count(), items.stream().filter(i -> i.lastContactAt() == null && i.overdueAmount().signum() > 0).count());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---- statement and agreement ----

    @Transactional
    public byte[] statementPdf(Long planId) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        syncWithInvoice(p, today());
        return printFiles.statement(detail(p), currency());
    }

    @Transactional
    public byte[] agreementPdf(Long planId) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        Customer c = p.getCustomerId() == null ? null : customerRepository.findById(p.getCustomerId()).orElse(null);
        return printFiles.agreement(detail(p), c != null ? c.getPhone() : null, settingsEntity().getAgreementTerms(), currency());
    }

    private String currency() {
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        return store != null && store.getCurrencyCode() != null ? store.getCurrencyCode().trim() : "";
    }

    /** E-mails the customer their statement (and, if asked, the agreement) as PDFs. */
    @Transactional
    public ReminderResultDto emailStatement(Long planId, boolean includeAgreement, AppUser caller) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        Customer customer = p.getCustomerId() == null ? null : customerRepository.findById(p.getCustomerId()).orElse(null);
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw ApiException.badRequest("This customer has no email address.");
        }
        if (!mailService.isConfigured()) {
            throw ApiException.badRequest("Email isn't configured yet — set up SMTP in Admin > Settings > Email first");
        }
        PlanDetailDto d = detail(p);
        String cur = currency();
        List<EmailAttachment> files = new ArrayList<>();
        files.add(new EmailAttachment("statement-" + p.getPlanNumber() + ".pdf", "application/pdf", printFiles.statement(d, cur)));
        if (includeAgreement) {
            files.add(new EmailAttachment("agreement-" + p.getPlanNumber() + ".pdf", "application/pdf",
                    printFiles.agreement(d, customer.getPhone(), settingsEntity().getAgreementTerms(), cur)));
        }
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        String business = store != null && store.getBusinessName() != null ? store.getBusinessName() : "";
        String c = cur.isEmpty() ? "" : cur + " ";
        String body = "<p>Dear " + esc(customer.getName()) + ",</p><p>Please find attached your instalment statement for invoice <b>" + esc(p.getInvoiceNumber()) + "</b>"
                + (includeAgreement ? " and a copy of your instalment agreement" : "") + ".</p><p>Paid so far: <b>" + esc(c) + money(d.plan().paid()) + "</b><br>Still to pay: <b>"
                + esc(c) + money(d.plan().outstanding()) + "</b>" + (d.plan().overdueAmount().signum() > 0 ? "<br>Overdue now: <b>" + esc(c) + money(d.plan().overdueAmount()) + "</b>" : "")
                + "</p><p>" + esc(business) + "</p>";
        mailService.send(customer.getEmail(), "Your instalment statement — " + p.getInvoiceNumber(), body, files);
        logEmailContact(p, "Statement" + (includeAgreement ? " and agreement" : "") + " e-mailed to " + customer.getEmail(), caller);
        auditLogService.record("INSTALLMENT_STATEMENT_SENT", "E-mailed the statement of instalment plan " + p.getPlanNumber() + " to " + customer.getEmail(), "InstallmentPlan", p.getId());
        return new ReminderResultDto(true, customer.getEmail());
    }

    // ---- the working-out ----

    /** One instalment with what has been paid on it. */
    private static final class Row {
        final Installment i;
        BigDecimal principalPaid = ZERO;
        BigDecimal chargesPaid = ZERO;
        BigDecimal principalLeft = ZERO;
        BigDecimal chargesLeft = ZERO;
        InstallmentState state;
        long daysOverdue;

        Row(Installment i) {
            this.i = i;
        }

        BigDecimal interestLeft() {
            // Interest is settled before late fees, so what was paid in charges goes to the interest first.
            return i.getInterestDue().subtract(chargesPaid).max(ZERO);
        }

        BigDecimal total() {
            return i.getPrincipalDue().add(i.getInterestDue()).add(i.getLateFee());
        }

        BigDecimal paid() {
            return principalPaid.add(chargesPaid);
        }

        BigDecimal left() {
            return principalLeft.add(chargesLeft);
        }
    }

    private List<Row> view(InstallmentPlan p, LocalDate today) {
        BigDecimal invoicePaid = invoiceRepository.findById(p.getInvoiceId()).map(customerCreditService::amountPaidForInvoice).orElse(p.getBaselinePaid());
        BigDecimal principalPool = invoicePaid.subtract(p.getBaselinePaid()).max(ZERO);
        BigDecimal chargesPool = paymentRepository.sumChargesPart(p.getId());
        List<Row> rows = new ArrayList<>();
        for (Installment i : p.getInstallments().stream().sorted(Comparator.comparingInt(Installment::getSeq)).toList()) {
            Row r = new Row(i);
            r.principalPaid = principalPool.min(i.getPrincipalDue());
            principalPool = principalPool.subtract(r.principalPaid);
            BigDecimal charges = i.getInterestDue().add(i.getLateFee());
            r.chargesPaid = chargesPool.min(charges);
            chargesPool = chargesPool.subtract(r.chargesPaid);
            r.principalLeft = i.getPrincipalDue().subtract(r.principalPaid);
            r.chargesLeft = charges.subtract(r.chargesPaid);
            BigDecimal left = r.left();
            r.daysOverdue = left.signum() > 0 && i.getDueDate().isBefore(today) ? ChronoUnit.DAYS.between(i.getDueDate(), today) : 0;
            r.state = left.signum() <= 0 ? InstallmentState.PAID
                    : i.getDueDate().isBefore(today) ? InstallmentState.OVERDUE
                    : r.paid().signum() > 0 ? InstallmentState.PARTIAL : InstallmentState.PENDING;
            rows.add(r);
        }
        return rows;
    }

    private PlanSummaryDto summary(InstallmentPlan p, List<Row> rows, LocalDate today) {
        BigDecimal totalPayable = rows.stream().map(Row::total).reduce(ZERO, BigDecimal::add);
        BigDecimal paid = rows.stream().map(Row::paid).reduce(ZERO, BigDecimal::add);
        boolean active = isOpen(p);
        BigDecimal outstanding = p.getStatus() == InstallmentPlanStatus.WRITTEN_OFF ? ZERO : rows.stream().map(Row::left).reduce(ZERO, BigDecimal::add);
        BigDecimal overdue = rows.stream().filter(r -> r.state == InstallmentState.OVERDUE).map(Row::left).reduce(ZERO, BigDecimal::add);
        Row next = rows.stream().filter(r -> r.left().signum() > 0).findFirst().orElse(null);
        int paidCount = (int) rows.stream().filter(r -> r.state == InstallmentState.PAID).count();
        int overdueCount = (int) rows.stream().filter(r -> r.state == InstallmentState.OVERDUE).count();
        return new PlanSummaryDto(p.getId(), p.getPlanNumber(), p.getInvoiceId(), p.getInvoiceNumber(), p.getCustomerId(), p.getCustomerName(), p.getBranchId(),
                p.getStatus(), p.getFinancedAmount(), p.getTotalInterest(), totalPayable, paid, outstanding,
                active ? overdue : ZERO, active && next != null ? next.i.getDueDate() : null, active && next != null ? next.left() : null,
                p.getNumberOfInstallments(), paidCount, active ? overdueCount : 0, p.getCreatedAt());
    }

    // ---- small helpers ----

    private InstallmentPlan entity(Long id) {
        return planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Instalment plan not found"));
    }

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private static LocalDate today() {
        return LocalDate.now(zone());
    }

    private static int inRange(int v, int min, int max, String what) {
        if (v < min || v > max) {
            throw ApiException.badRequest(what + " must be between " + min + " and " + max + ".");
        }
        return v;
    }

    private static BigDecimal nonNegative(BigDecimal v, String what) {
        if (v.signum() < 0) {
            throw ApiException.badRequest(what + " can't be negative.");
        }
        return v;
    }

    private static BigDecimal percent(BigDecimal v, String what) {
        if (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest(what + " must be between 0 and 100.");
        }
        return v;
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.##").format(v);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
