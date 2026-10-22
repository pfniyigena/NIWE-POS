package com.posapp.hr;

import com.posapp.accounting.AccountService;
import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.common.ExportFiles;
import com.posapp.hr.PayrollExtrasDtos.*;
import com.posapp.reports.ReportExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * What the payroll withholds and owes to the authorities (income tax, social security, other payables), what has been paid over, and the
 * monthly return schedules. The amounts owed come from the approved payrolls, exactly as they were posted to Accounting, so the three
 * "still owed" figures always agree with the matching liability accounts.
 */
@Service
public class PayrollRemittanceService {

    /** Tax and contributions of a month are due by this day of the next month. */
    static final int DUE_DAY = 15;
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    private final PayrollRemittanceRepository repository;
    private final PayrollRunRepository runRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final BranchAccessService branchAccessService;
    private final ReportExportService exportService;

    public PayrollRemittanceService(PayrollRemittanceRepository repository, PayrollRunRepository runRepository, EmployeeRepository employeeRepository,
                                    AccountingService accountingService, AuditLogService auditLogService, BranchAccessService branchAccessService, ReportExportService exportService) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.employeeRepository = employeeRepository;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.branchAccessService = branchAccessService;
        this.exportService = exportService;
    }

    static String label(RemittanceKind k) {
        return switch (k) {
            case PAYE -> "Income tax (PAYE)";
            case SOCIAL_SECURITY -> "Social security";
            case OTHER -> "Other payroll payables";
        };
    }

    private static String accountKey(RemittanceKind k) {
        return switch (k) {
            case PAYE -> AccountService.PAYE_PAYABLE;
            case SOCIAL_SECURITY -> AccountService.SOCIAL_SECURITY_PAYABLE;
            case OTHER -> AccountService.HEALTH_OTHER_PAYROLL_PAYABLE;
        };
    }

    /** What an approved payroll owes, split the same way the approval posted it. */
    static Map<RemittanceKind, BigDecimal> owed(PayrollRun run) {
        BigDecimal paye = BigDecimal.ZERO, ss = BigDecimal.ZERO, other = BigDecimal.ZERO;
        for (PayrollLine l : run.getLines()) {
            paye = paye.add(l.getPayeTax());
            for (PayrollItem i : l.getItems()) {
                if (i.getKind() == ItemKind.DEDUCTION || i.getKind() == ItemKind.EMPLOYER) {
                    if (i.getTarget() == PayableTarget.SOCIAL_SECURITY) ss = ss.add(i.getAmount());
                    else if (i.getTarget() != PayableTarget.STAFF_ADVANCE) other = other.add(i.getAmount());
                }
            }
        }
        Map<RemittanceKind, BigDecimal> m = new EnumMap<>(RemittanceKind.class);
        m.put(RemittanceKind.PAYE, paye);
        m.put(RemittanceKind.SOCIAL_SECURITY, ss);
        m.put(RemittanceKind.OTHER, other);
        return m;
    }

    private static LocalDate dueDate(int year, int month) {
        return YearMonth.of(year, month).plusMonths(1).atDay(DUE_DAY);
    }

    private Map<String, BigDecimal> paidIndex() {
        Map<String, BigDecimal> paid = new HashMap<>();
        for (PayrollRemittance r : repository.findByStatus("ACTIVE")) paid.merge(key(r.getKind(), r.getPeriodYear(), r.getPeriodMonth()), r.getAmount(), BigDecimal::add);
        return paid;
    }

    private static String key(RemittanceKind k, int y, int m) {
        return k + "|" + y + "|" + m;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public RemittanceSummaryDto summary(Integer year) {
        assertCompanyWide();
        Map<String, BigDecimal> paid = paidIndex();
        LocalDate today = LocalDate.now();
        List<RemittanceRowDto> rows = new ArrayList<>();
        Map<RemittanceKind, BigDecimal[]> totals = new EnumMap<>(RemittanceKind.class);
        for (RemittanceKind k : RemittanceKind.values()) totals.put(k, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        List<PayrollRun> runs = new ArrayList<>(runRepository.findAllByOrderByPeriodYearDescPeriodMonthDesc());
        runs.sort(Comparator.comparingInt(PayrollRun::getPeriodYear).thenComparingInt(PayrollRun::getPeriodMonth));
        for (PayrollRun run : runs) {
            if (run.getStatus() == PayrollStatus.DRAFT) continue;
            Map<RemittanceKind, BigDecimal> owed = owed(run);
            for (RemittanceKind k : RemittanceKind.values()) {
                BigDecimal o = owed.get(k);
                BigDecimal p = paid.getOrDefault(key(k, run.getPeriodYear(), run.getPeriodMonth()), BigDecimal.ZERO);
                if (o.signum() == 0 && p.signum() == 0) continue;
                BigDecimal left = o.subtract(p);
                LocalDate due = dueDate(run.getPeriodYear(), run.getPeriodMonth());
                boolean overdue = left.signum() > 0 && today.isAfter(due);
                BigDecimal[] t = totals.get(k);
                t[0] = t[0].add(o); t[1] = t[1].add(p); t[2] = t[2].add(left); if (overdue) t[3] = t[3].add(left);
                if (year == null || run.getPeriodYear() == year) {
                    rows.add(new RemittanceRowDto(run.getPeriodYear(), run.getPeriodMonth(), run.getId(), run.getStatus(), k, label(k), o, p, left, due, overdue));
                }
            }
        }
        List<KindTotalDto> tot = new ArrayList<>();
        for (RemittanceKind k : RemittanceKind.values()) {
            BigDecimal[] t = totals.get(k);
            tot.add(new KindTotalDto(k, label(k), t[0], t[1], t[2], t[3]));
        }
        return new RemittanceSummaryDto(tot, rows);
    }

    @Transactional(readOnly = true)
    public List<RemittanceDto> list(RemittanceKind kind, Integer year, Integer month, String status, String q) {
        assertCompanyWide();
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<RemittanceDto> out = new ArrayList<>();
        for (PayrollRemittance r : repository.findAllByOrderByPaidOnDescIdDesc()) {
            if (kind != null && r.getKind() != kind) continue;
            if (year != null && r.getPeriodYear() != year) continue;
            if (month != null && r.getPeriodMonth() != month) continue;
            if (status != null && !status.isBlank() && !r.getStatus().equalsIgnoreCase(status)) continue;
            if (!needle.isEmpty() && !((nz(r.getReference()) + " " + nz(r.getPaidTo()) + " " + nz(r.getNote())).toLowerCase().contains(needle))) continue;
            out.add(dto(r));
        }
        return out;
    }

    // ---- paying ----

    @Transactional
    public RemittanceDto record(RemittanceRequest req) {
        assertCompanyWide();
        if (req == null || req.kind() == null) throw ApiException.badRequest("Choose what is being paid (income tax, social security or other).");
        if (req.year() == null || req.month() == null || req.month() < 1 || req.month() > 12 || req.year() < 2000 || req.year() > 2200) throw ApiException.badRequest("Choose the payroll month it settles.");
        if (req.amount() == null || req.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount paid.");
        BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
        PayrollRun run = runRepository.findByPeriodYearAndPeriodMonth(req.year(), req.month()).filter(r -> r.getStatus() != PayrollStatus.DRAFT)
                .orElseThrow(() -> ApiException.badRequest("There is no approved payroll for " + MONTHS[req.month() - 1] + " " + req.year() + "."));
        BigDecimal owed = owed(run).get(req.kind());
        BigDecimal paid = paidIndex().getOrDefault(key(req.kind(), req.year(), req.month()), BigDecimal.ZERO);
        BigDecimal left = owed.subtract(paid);
        if (amount.compareTo(left) > 0) {
            throw ApiException.badRequest("Only " + left.setScale(2, RoundingMode.HALF_UP).toPlainString() + " is still owed for " + label(req.kind()) + " " + MONTHS[req.month() - 1] + " " + req.year() + ".");
        }
        PayrollRemittance r = new PayrollRemittance();
        r.setKind(req.kind());
        r.setPeriodYear(req.year());
        r.setPeriodMonth(req.month());
        r.setAmount(amount);
        r.setPaidOn(req.paidOn() != null ? req.paidOn() : LocalDate.now());
        r.setReference(HrSupport.blankToNull(req.reference()));
        r.setPaidTo(HrSupport.blankToNull(req.paidTo()));
        r.setNote(HrSupport.blankToNull(req.note()));
        r.setCreatedByName(HrSupport.currentUserName());
        r = repository.save(r);
        accountingService.postPayrollRemittance("PAYROLL_REMITTANCE", r.getId(), label(r.getKind()) + " paid " + MONTHS[r.getPeriodMonth() - 1] + " " + r.getPeriodYear() + (r.getPaidTo() != null ? " — " + r.getPaidTo() : ""),
                accountKey(r.getKind()), amount, r.getPaidOn(), false);
        auditLogService.record("PAYROLL_REMITTED", "Paid " + amount + " of " + label(r.getKind()) + " for " + MONTHS[r.getPeriodMonth() - 1] + " " + r.getPeriodYear(), "PayrollRemittance", r.getId());
        return dto(r);
    }

    /** A payment entered by mistake: the books are reversed and the amount is owed again. */
    @Transactional
    public RemittanceDto voidIt(Long id, String note) {
        assertCompanyWide();
        PayrollRemittance r = repository.findById(id).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!"ACTIVE".equals(r.getStatus())) throw ApiException.conflict("This payment is already cancelled.");
        if (note == null || note.isBlank()) throw ApiException.badRequest("Say why the payment is cancelled.");
        r.setStatus("VOIDED");
        r.setVoidNote(note.trim());
        r.setVoidedByName(HrSupport.currentUserName());
        repository.save(r);
        accountingService.postPayrollRemittance("PAYROLL_REMITTANCE_VOID", r.getId(), "Cancelled payment of " + label(r.getKind()) + " " + MONTHS[r.getPeriodMonth() - 1] + " " + r.getPeriodYear(),
                accountKey(r.getKind()), r.getAmount(), LocalDate.now(), true);
        auditLogService.record("PAYROLL_REMITTANCE_VOIDED", "Cancelled the payment of " + r.getAmount() + " (" + label(r.getKind()) + " " + MONTHS[r.getPeriodMonth() - 1] + " " + r.getPeriodYear() + "): " + note.trim(), "PayrollRemittance", id);
        return dto(r);
    }

    // ---- return schedules ----

    /** The income-tax return of a month: one row per employee with the tax number, taxable pay and the tax withheld. */
    @Transactional
    public ResponseEntity<byte[]> payeReturn(Long runId, String format) {
        PayrollRun run = approvedRun(runId);
        List<String> h = List.of("No.", "Employee", "Tax number", "Gross pay", "Taxable pay", "PAYE withheld");
        List<List<String>> body = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO, taxable = BigDecimal.ZERO, paye = BigDecimal.ZERO;
        for (PayrollLine l : sorted(run)) {
            Employee e = l.getEmployeeId() == null ? null : employeeRepository.findById(l.getEmployeeId()).orElse(null);
            body.add(List.of(nz(l.getEmployeeNumber()), nz(l.getEmployeeName()), e == null ? "" : nz(e.getTaxNumber()), n2(l.getGrossPay()), n2(l.getTaxablePay()), n2(l.getPayeTax())));
            gross = gross.add(l.getGrossPay()); taxable = taxable.add(l.getTaxablePay()); paye = paye.add(l.getPayeTax());
        }
        body.add(List.of("", "TOTAL (" + run.getLines().size() + ")", "", n2(gross), n2(taxable), n2(paye)));
        auditLogService.record("PAYROLL_RETURN", "Downloaded the income-tax return of " + period(run), "PayrollRun", runId);
        return ExportFiles.file(exportService, "Income tax (PAYE) return " + period(run), "paye-return-" + run.getPeriodYear() + "-" + String.format("%02d", run.getPeriodMonth()), h, body, format);
    }

    /** The social-security return of a month: one row per employee with the employee's and the employer's contributions. */
    @Transactional
    public ResponseEntity<byte[]> socialSecurityReturn(Long runId, String format) {
        PayrollRun run = approvedRun(runId);
        List<String> h = List.of("No.", "Employee", "Social security no.", "Gross pay", "Employee contribution", "Employer contribution", "Total");
        List<List<String>> body = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO, ee = BigDecimal.ZERO, er = BigDecimal.ZERO;
        for (PayrollLine l : sorted(run)) {
            BigDecimal pe = BigDecimal.ZERO, pr = BigDecimal.ZERO;
            for (PayrollItem i : l.getItems()) {
                if (i.getTarget() != PayableTarget.SOCIAL_SECURITY) continue;
                if (i.getKind() == ItemKind.DEDUCTION) pe = pe.add(i.getAmount());
                else if (i.getKind() == ItemKind.EMPLOYER) pr = pr.add(i.getAmount());
            }
            Employee e = l.getEmployeeId() == null ? null : employeeRepository.findById(l.getEmployeeId()).orElse(null);
            body.add(List.of(nz(l.getEmployeeNumber()), nz(l.getEmployeeName()), e == null ? "" : nz(e.getSocialSecurityNumber()), n2(l.getGrossPay()), n2(pe), n2(pr), n2(pe.add(pr))));
            gross = gross.add(l.getGrossPay()); ee = ee.add(pe); er = er.add(pr);
        }
        body.add(List.of("", "TOTAL (" + run.getLines().size() + ")", "", n2(gross), n2(ee), n2(er), n2(ee.add(er))));
        auditLogService.record("PAYROLL_RETURN", "Downloaded the social-security return of " + period(run), "PayrollRun", runId);
        return ExportFiles.file(exportService, "Social security return " + period(run), "social-security-return-" + run.getPeriodYear() + "-" + String.format("%02d", run.getPeriodMonth()), h, body, format);
    }

    // ---- internals ----

    private PayrollRun approvedRun(Long runId) {
        assertCompanyWide();
        PayrollRun run = runRepository.findById(runId).orElseThrow(() -> ApiException.notFound("Payroll run not found"));
        if (run.getStatus() == PayrollStatus.DRAFT) throw ApiException.badRequest("Approve the payroll first: returns are made from approved payrolls.");
        return run;
    }

    private static List<PayrollLine> sorted(PayrollRun run) {
        return run.getLines().stream().sorted(Comparator.comparing(PayrollLine::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
    }

    private static String period(PayrollRun run) {
        return MONTHS[run.getPeriodMonth() - 1] + " " + run.getPeriodYear();
    }

    private RemittanceDto dto(PayrollRemittance r) {
        return new RemittanceDto(r.getId(), r.getKind(), label(r.getKind()), r.getPeriodYear(), r.getPeriodMonth(), r.getAmount(), r.getPaidOn(), r.getReference(), r.getPaidTo(), r.getNote(),
                r.getStatus(), r.getVoidNote(), r.getCreatedByName());
    }

    private static String n2(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void assertCompanyWide() {
        if (branchAccessService.isRestricted()) {
            throw ApiException.forbidden("Payroll covers all branches — it's only available to users who aren't limited to certain branches.");
        }
    }
}
