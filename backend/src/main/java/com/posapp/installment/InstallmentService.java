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

    public InstallmentService(InstallmentPlanRepository planRepository, InstallmentPaymentRepository paymentRepository,
                              InstallmentSettingsRepository settingsRepository, InvoiceRepository invoiceRepository,
                              CustomerRepository customerRepository, CustomerCreditService customerCreditService,
                              AccountingService accountingService, MailService mailService, StoreSettingsRepository storeSettingsRepository,
                              ReportExportService exportService, BranchAccessService branchAccessService, AuditLogService auditLogService,
                              ModuleService moduleService) {
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
        settingsRepository.save(s);
        auditLogService.record("INSTALLMENT_SETTINGS_UPDATED", "Updated the instalment settings");
        return toDto(s);
    }

    private static SettingsDto toDto(InstallmentSettings s) {
        return new SettingsDto(s.isRemindersEnabled(), s.getReminderDaysBefore(), s.getOverdueReminderEveryDays(), s.isDefaultWholeUnits(), s.getDefaultInterestRate(),
                s.getDefaultGraceDays(), s.getDefaultLateFeeFixed(), s.getDefaultLateFeePercent());
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
                r.i.getInterestDue(), r.i.getLateFee(), r.i.isLateFeeWaived(), r.total(), r.paid(), r.left(), r.state, r.daysOverdue)).toList();
        List<PaymentDto> payments = paymentRepository.findByPlanIdOrderByPaidAtDescIdDesc(p.getId()).stream()
                .map(x -> new PaymentDto(x.getId(), x.getPaidAt(), x.getAmount(), x.getPrincipalPart(), x.getChargesPart(), x.getMethod(),
                        x.getReceiptNumber(), x.getNote(), x.getRecordedByName())).toList();
        TermsDto terms = new TermsDto(p.getInvoiceTotal(), p.getDeposit(), p.getInterestMethod(), p.getInterestRate(), p.getFrequency(),
                p.getFirstDueDate(), p.getGraceDays(), p.getLateFeeFixed(), p.getLateFeePercent(), p.isRemindersEnabled(), p.isWholeUnits(), p.getNotes(),
                p.getAdjustmentNote(), p.getCreatedByName(), p.getCancelledOn(), p.getCancelReason());
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
        for (InstallmentPlan p : planRepository.findByStatus(InstallmentPlanStatus.ACTIVE)) {
            if (!branchAccessService.canAccess(p.getBranchId())) {
                continue;
            }
            active++;
            for (Row r : view(p, today)) {
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
            if (branchAccessService.canAccess(x.getPlan().getBranchId())) {
                collected = collected.add(x.getAmount());
            }
        }
        return new SummaryDto(active, outstanding, overdue, overdueCount, dueThisMonth, collected);
    }

    /** Every instalment of every plan as flat lines (the reports filter and group them). */
    @Transactional
    public List<InstallmentLine> lines(boolean activeOnly, Long branchId) {
        LocalDate today = today();
        refreshActive(today);
        List<InstallmentLine> out = new ArrayList<>();
        for (InstallmentPlan p : planRepository.findAllByOrderByIdDesc()) {
            if ((activeOnly && p.getStatus() != InstallmentPlanStatus.ACTIVE) || !branchAccessService.canAccess(p.getBranchId())
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
        if (planRepository.existsByInvoiceIdAndStatus(invoice.getId(), InstallmentPlanStatus.ACTIVE)) {
            throw ApiException.conflict("Invoice " + invoice.getInvoiceNumber() + " already has an active instalment plan.");
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
        if (p.getStatus() == InstallmentPlanStatus.ACTIVE && syncWithInvoice(p, today())) {
            throw ApiException.conflict("The invoice is no longer payable, so this plan was cancelled: " + p.getCancelReason());
        }
        if (p.getStatus() != InstallmentPlanStatus.ACTIVE) {
            throw ApiException.conflict("This plan is " + p.getStatus().name().toLowerCase() + " — payments can't be recorded on it.");
        }
        if (r.amount() == null || r.amount().signum() <= 0) {
            throw ApiException.badRequest("Enter the amount received.");
        }
        if (r.method() == null || r.method().isBlank()) {
            throw ApiException.badRequest("Choose the payment method.");
        }
        BigDecimal amount = InstallmentCalculator.round(r.amount());
        LocalDate today = today();
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
        pay.setMethod(r.method());
        pay.setNote(blankToNull(r.note()));
        pay.setRecordedByName(caller != null ? caller.getFullName() : null);
        if (principalPart.signum() > 0) {
            RecordInvoicePaymentRequest inv = new RecordInvoicePaymentRequest();
            inv.setAmount(principalPart);
            inv.setMethod(r.method());
            inv.setNote("Instalment plan " + p.getPlanNumber() + (r.note() != null && !r.note().isBlank() ? " — " + r.note().trim() : ""));
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
        return detail(p);
    }

    @Transactional
    public PlanDetailDto cancel(Long planId, String reason) {
        InstallmentPlan p = entity(planId);
        branchAccessService.assertCanAccess(p.getBranchId());
        if (p.getStatus() != InstallmentPlanStatus.ACTIVE) {
            throw ApiException.conflict("Only an active plan can be cancelled.");
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
        for (InstallmentPlan p : planRepository.findByStatus(InstallmentPlanStatus.ACTIVE)) {
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
        if (p.getStatus() != InstallmentPlanStatus.ACTIVE) {
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
        if (p.getStatus() != InstallmentPlanStatus.ACTIVE) {
            throw ApiException.conflict("Only an active plan can be rescheduled.");
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
    public void nightly() {
        if (!moduleService.isEnabled(ModuleKey.INSTALLMENTS)) {
            return;
        }
        LocalDate today = today();
        for (InstallmentPlan p : planRepository.findByStatus(InstallmentPlanStatus.ACTIVE)) {
            try {
                if (syncWithInvoice(p, today)) {
                    continue;
                }
                assessLateFees(p, today);
                refreshStatus(p, today);
            } catch (RuntimeException e) {
                log.warn("Instalment plan {} could not be updated: {}", p.getPlanNumber(), e.getMessage());
            }
        }
    }

    private void refreshStatus(InstallmentPlan p, LocalDate today) {
        if (p.getStatus() == InstallmentPlanStatus.ACTIVE && view(p, today).stream().allMatch(r -> r.left().signum() <= 0)) {
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
        boolean active = p.getStatus() == InstallmentPlanStatus.ACTIVE;
        BigDecimal outstanding = rows.stream().map(Row::left).reduce(ZERO, BigDecimal::add);
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
