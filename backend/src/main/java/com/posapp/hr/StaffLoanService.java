package com.posapp.hr;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.PayrollExtrasDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Salary advances and staff loans. Giving one out is a cash payment that is owed back by the employee; every payroll then takes the
 * installment from the pay until nothing is left. The balance is always the amount given minus the repayments on record.
 */
@Service
public class StaffLoanService {

    private final StaffLoanRepository loanRepository;
    private final StaffLoanRepaymentRepository repaymentRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final BranchAccessService branchAccessService;

    public StaffLoanService(StaffLoanRepository loanRepository, StaffLoanRepaymentRepository repaymentRepository, EmployeeRepository employeeRepository,
                            AccountingService accountingService, AuditLogService auditLogService, BranchAccessService branchAccessService) {
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
        this.employeeRepository = employeeRepository;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.branchAccessService = branchAccessService;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<LoanDto> list(LoanStatus status, Long employeeId, LoanType type, String q) {
        assertCompanyWide();
        Map<Long, BigDecimal> repaid = repaidByLoan();
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<LoanDto> out = new ArrayList<>();
        for (StaffLoan l : loanRepository.findAllByOrderByIdDesc()) {
            if (status != null && l.getStatus() != status) continue;
            if (employeeId != null && !l.getEmployee().getId().equals(employeeId)) continue;
            if (type != null && l.getLoanType() != type) continue;
            if (!needle.isEmpty() && !(nz(l.getLoanNumber()) + " " + nz(l.getEmployee().fullName()) + " " + nz(l.getEmployee().getEmployeeNumber()) + " " + nz(l.getNote())).toLowerCase().contains(needle)) continue;
            out.add(dto(l, repaid.getOrDefault(l.getId(), BigDecimal.ZERO)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public LoanDetailDto get(Long id) {
        assertCompanyWide();
        StaffLoan l = loan(id);
        List<StaffLoanRepayment> reps = repaymentRepository.findByLoanIdOrderByRepaidOnAscIdAsc(id);
        BigDecimal repaid = reps.stream().map(StaffLoanRepayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LoanDetailDto(dto(l, repaid), reps.stream().map(r -> new RepaymentDto(r.getId(), r.getAmount(), r.getRepaidOn(), r.getSource(), r.getRunId(), r.getNote(), r.getCreatedByName())).toList());
    }

    // ---- giving and settling ----

    @Transactional
    public LoanDto create(LoanRequest req) {
        assertCompanyWide();
        if (req == null || req.employeeId() == null) throw ApiException.badRequest("Choose the employee.");
        Employee e = employeeRepository.findById(req.employeeId()).orElseThrow(() -> ApiException.notFound("Employee not found"));
        if (e.getStatus() != EmployeeStatus.ACTIVE || (e.getTerminationDate() != null && e.getTerminationDate().isBefore(LocalDate.now()))) {
            throw ApiException.badRequest(e.fullName() + " is not an active employee.");
        }
        LoanType type = req.loanType() != null ? req.loanType() : LoanType.LOAN;
        if (req.principal() == null || req.principal().signum() <= 0) throw ApiException.badRequest("Enter the amount given.");
        BigDecimal principal = req.principal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal installment = req.installment() != null ? req.installment().setScale(2, RoundingMode.HALF_UP) : (type == LoanType.ADVANCE ? principal : null);
        if (installment == null || installment.signum() <= 0) throw ApiException.badRequest("Enter the monthly installment.");
        if (installment.compareTo(principal) > 0) throw ApiException.badRequest("The installment cannot be more than the amount given.");
        if (principal.divide(installment, 0, RoundingMode.CEILING).compareTo(BigDecimal.valueOf(120)) > 0) {
            throw ApiException.badRequest("That would take more than 120 months to repay. Raise the installment.");
        }
        LocalDate issue = req.issueDate() != null ? req.issueDate() : LocalDate.now();
        YearMonth first = req.firstYear() != null && req.firstMonth() != null ? validMonth(req.firstYear(), req.firstMonth())
                : (type == LoanType.ADVANCE ? YearMonth.from(issue) : YearMonth.from(issue).plusMonths(1));
        if (first.isBefore(YearMonth.from(issue))) throw ApiException.badRequest("The first deduction cannot be before the month the money is given.");

        StaffLoan l = new StaffLoan();
        l.setEmployee(e);
        l.setLoanType(type);
        l.setPrincipal(principal);
        l.setInstallment(installment);
        l.setIssueDate(issue);
        l.setFirstYear(first.getYear());
        l.setFirstMonth(first.getMonthValue());
        l.setBranchId(e.getBranchId());
        l.setNote(HrSupport.blankToNull(req.note()));
        l.setCreatedByName(HrSupport.currentUserName());
        l = loanRepository.save(l);
        l.setLoanNumber("LN-" + String.format("%04d", l.getId()));
        l = loanRepository.save(l);
        accountingService.postStaffAdvance(l.getId(), (type == LoanType.ADVANCE ? "Salary advance " : "Staff loan ") + l.getLoanNumber() + " — " + e.fullName(), principal, issue, e.getBranchId());
        auditLogService.record("STAFF_LOAN_GIVEN", (type == LoanType.ADVANCE ? "Gave a salary advance of " : "Gave a staff loan of ") + principal + " to " + e.fullName() + " (" + l.getLoanNumber() + ")", "StaffLoan", l.getId());
        return dto(l, BigDecimal.ZERO);
    }

    /** The employee pays some or all of it back directly, in cash. */
    @Transactional
    public LoanDetailDto repayInCash(Long id, RepayRequest req) {
        assertCompanyWide();
        StaffLoan l = loan(id);
        requireActive(l);
        BigDecimal balance = balance(l);
        if (req == null || req.amount() == null || req.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount paid back.");
        BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(balance) > 0) throw ApiException.badRequest("Only " + balance + " is still owed.");
        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        addRepayment(l, amount, date, RepaymentSource.CASH, null, HrSupport.blankToNull(req.note()));
        accountingService.postStaffAdvanceBack("STAFF_ADVANCE_REPAID", l.getId(), "Repayment of " + l.getLoanNumber() + " — " + l.getEmployee().fullName(), amount, date, l.getBranchId(), false);
        refreshStatus(l);
        auditLogService.record("STAFF_LOAN_REPAID", "Received " + amount + " in cash on " + l.getLoanNumber() + " from " + l.getEmployee().fullName(), "StaffLoan", id);
        return get(id);
    }

    /** What is left will not be collected (for example the employee has left): it becomes a cost. */
    @Transactional
    public LoanDetailDto writeOff(Long id, String note) {
        assertCompanyWide();
        StaffLoan l = loan(id);
        requireActive(l);
        BigDecimal balance = balance(l);
        if (note == null || note.isBlank()) throw ApiException.badRequest("Say why it is written off.");
        addRepayment(l, balance, LocalDate.now(), RepaymentSource.WRITE_OFF, null, note.trim());
        accountingService.postStaffAdvanceBack("STAFF_ADVANCE_WRITE_OFF", l.getId(), "Written off " + l.getLoanNumber() + " — " + l.getEmployee().fullName(), balance, LocalDate.now(), l.getBranchId(), true);
        refreshStatus(l);
        auditLogService.record("STAFF_LOAN_WRITTEN_OFF", "Wrote off " + balance + " of " + l.getLoanNumber() + " (" + l.getEmployee().fullName() + "): " + note.trim(), "StaffLoan", id);
        return get(id);
    }

    /** Taking back a loan that was given by mistake: only possible while nothing has been repaid. */
    @Transactional
    public LoanDto cancel(Long id) {
        assertCompanyWide();
        StaffLoan l = loan(id);
        requireActive(l);
        if (!repaymentRepository.findByLoanIdOrderByRepaidOnAscIdAsc(id).isEmpty()) {
            throw ApiException.conflict("Something has already been repaid on this loan, so it can't be cancelled. Record the repayment or write off the rest instead.");
        }
        l.setStatus(LoanStatus.CANCELLED);
        loanRepository.save(l);
        accountingService.postStaffAdvanceBack("STAFF_ADVANCE_CANCELLED", l.getId(), "Cancelled " + l.getLoanNumber() + " — " + l.getEmployee().fullName(), l.getPrincipal(), LocalDate.now(), l.getBranchId(), false);
        auditLogService.record("STAFF_LOAN_CANCELLED", "Cancelled " + l.getLoanNumber() + " (" + l.getEmployee().fullName() + ")", "StaffLoan", id);
        return dto(l, BigDecimal.ZERO);
    }

    // ---- used by payroll ----

    /** What each employee's loans take in the given month (the payroll being worked out): active loans whose first month has come and that still have a balance. */
    @Transactional(readOnly = true)
    public Map<Long, List<Due>> dueMap(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Map<Long, BigDecimal> repaid = repaidByLoan();
        Map<Long, List<Due>> out = new HashMap<>();
        for (StaffLoan l : loanRepository.findByStatus(LoanStatus.ACTIVE)) {
            boolean settleNow = l.getSettleYear() != null && l.getSettleMonth() != null && l.getSettleYear() == year && l.getSettleMonth() == month;
            if (l.getIssueDate().isAfter(ym.atEndOfMonth())) continue;
            if (!settleNow && YearMonth.of(l.getFirstYear(), l.getFirstMonth()).isAfter(ym)) continue;
            BigDecimal balance = l.getPrincipal().subtract(repaid.getOrDefault(l.getId(), BigDecimal.ZERO));
            if (balance.signum() <= 0) continue;
            out.computeIfAbsent(l.getEmployee().getId(), k -> new ArrayList<>()).add(new Due(l, settleNow ? balance : l.getInstallment().min(balance), balance));
        }
        out.values().forEach(list -> list.sort(Comparator.comparing(d -> d.loan().getId())));
        return out;
    }

    /** Approving a payroll: every loan installment on it becomes a repayment on record. A loan that cannot cover its installment stops the approval. */
    @Transactional
    public void recordPayroll(PayrollRun run) {
        LocalDate date = LocalDate.of(run.getPeriodYear(), run.getPeriodMonth(), 1).withDayOfMonth(YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).lengthOfMonth());
        Map<Long, BigDecimal> taken = new LinkedHashMap<>();
        for (PayrollLine line : run.getLines()) {
            for (PayrollItem i : line.getItems()) {
                if (i.getLoanId() != null) taken.merge(i.getLoanId(), i.getAmount(), BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> t : taken.entrySet()) {
            StaffLoan l = loanRepository.findById(t.getKey()).orElseThrow(() -> ApiException.conflict("A loan on this payroll no longer exists. Generate the payroll again."));
            if (l.getStatus() != LoanStatus.ACTIVE || balance(l).compareTo(t.getValue()) < 0) {
                throw ApiException.conflict("Loan " + l.getLoanNumber() + " (" + l.getEmployee().fullName() + ") changed since this payroll was worked out. Generate the payroll again before approving.");
            }
            addRepayment(l, t.getValue(), date, RepaymentSource.PAYROLL, run.getId(), "Payroll " + YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()));
            refreshStatus(l);
        }
    }

    /** Taking a payroll back to draft: the repayments it recorded are removed, so the balances are what they were before it was approved. */
    @Transactional
    public void undoPayroll(PayrollRun run) {
        List<StaffLoanRepayment> taken = repaymentRepository.findByRunId(run.getId());
        java.util.Set<StaffLoan> touched = new java.util.LinkedHashSet<>();
        for (StaffLoanRepayment r : taken) {
            if (r.getSource() == RepaymentSource.PAYROLL) {
                touched.add(r.getLoan());
                repaymentRepository.delete(r);
            }
        }
        repaymentRepository.flush();
        for (StaffLoan l : touched) {
            if (l.getStatus() == LoanStatus.PAID_OFF && balance(l).signum() > 0) {
                l.setStatus(LoanStatus.ACTIVE);
                loanRepository.save(l);
            }
        }
    }

    /** Active loans of an employee with what is still owed on each (for the final-pay screen). */
    @Transactional(readOnly = true)
    public List<Due> activeLoansOf(Long employeeId) {
        List<Due> out = new ArrayList<>();
        for (StaffLoan l : loanRepository.findByStatus(LoanStatus.ACTIVE)) {
            if (!l.getEmployee().getId().equals(employeeId)) continue;
            BigDecimal b = balance(l);
            if (b.signum() > 0) out.add(new Due(l, l.getInstallment().min(b), b));
        }
        out.sort(Comparator.comparing(d -> d.loan().getId()));
        return out;
    }

    /** Sets (or clears, when {@code year} is null) the month in which an employee's active loans are taken back in full. */
    @Transactional
    public int settleInMonth(Long employeeId, Integer year, Integer month) {
        int n = 0;
        for (StaffLoan l : loanRepository.findByStatus(LoanStatus.ACTIVE)) {
            if (!l.getEmployee().getId().equals(employeeId)) continue;
            l.setSettleYear(year);
            l.setSettleMonth(month);
            loanRepository.save(l);
            n++;
        }
        return n;
    }

    // ---- internals ----

    private void addRepayment(StaffLoan l, BigDecimal amount, LocalDate date, RepaymentSource source, Long runId, String note) {
        StaffLoanRepayment r = new StaffLoanRepayment();
        r.setLoan(l);
        r.setAmount(amount);
        r.setRepaidOn(date);
        r.setSource(source);
        r.setRunId(runId);
        r.setNote(note);
        r.setCreatedByName(HrSupport.currentUserName());
        repaymentRepository.saveAndFlush(r);
    }

    private void refreshStatus(StaffLoan l) {
        if (l.getStatus() == LoanStatus.ACTIVE && balance(l).signum() <= 0) {
            l.setStatus(LoanStatus.PAID_OFF);
            loanRepository.save(l);
        }
    }

    BigDecimal balance(StaffLoan l) {
        BigDecimal repaid = repaymentRepository.findByLoanIdOrderByRepaidOnAscIdAsc(l.getId()).stream().map(StaffLoanRepayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return l.getPrincipal().subtract(repaid);
    }

    private Map<Long, BigDecimal> repaidByLoan() {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (StaffLoanRepayment r : repaymentRepository.findAll()) m.merge(r.getLoan().getId(), r.getAmount(), BigDecimal::add);
        return m;
    }

    private LoanDto dto(StaffLoan l, BigDecimal repaid) {
        BigDecimal balance = l.getPrincipal().subtract(repaid);
        Integer left = l.getStatus() == LoanStatus.ACTIVE && balance.signum() > 0 ? balance.divide(l.getInstallment(), 0, RoundingMode.CEILING).intValue() : null;
        Employee e = l.getEmployee();
        return new LoanDto(l.getId(), l.getLoanNumber(), e.getId(), e.getEmployeeNumber(), e.fullName(), l.getLoanType(), l.getPrincipal(), l.getInstallment(), l.getIssueDate(),
                l.getFirstYear(), l.getFirstMonth(), l.getStatus(), repaid, l.getStatus() == LoanStatus.CANCELLED ? BigDecimal.ZERO : balance, left, l.getNote(), l.getCreatedByName(), l.getCreatedAt());
    }

    private static YearMonth validMonth(int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2200) throw ApiException.badRequest("Choose a valid month for the first deduction.");
        return YearMonth.of(year, month);
    }

    private void requireActive(StaffLoan l) {
        if (l.getStatus() != LoanStatus.ACTIVE) throw ApiException.conflict("This loan is " + (l.getStatus() == LoanStatus.PAID_OFF ? "already paid off." : "cancelled."));
    }

    StaffLoan loan(Long id) {
        return loanRepository.findById(id).orElseThrow(() -> ApiException.notFound("Loan not found"));
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
