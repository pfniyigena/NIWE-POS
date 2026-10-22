package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.PayrollExtrasDtos.*;
import com.posapp.hr.dto.HrDtos.LeaveBalanceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Final pay for an employee who left: unused leave paid out, notice paid or recovered, loans taken back. The amounts reach the payroll of the
 * last month as adjustments (so they get income tax and contributions like any other pay) and the loans are settled by that payroll.
 */
@Service
public class FinalPayService {

    static final String SOURCE = "FINAL_PAY";
    private static final BigDecimal MAX_DAYS = new BigDecimal("366");

    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final EmployeeFinalPayRepository repository;
    private final PayrollAdjustmentRepository adjustmentRepository;
    private final PayrollRunRepository runRepository;
    private final LeaveService leaveService;
    private final HrSettingsService settingsService;
    private final StaffLoanService loanService;
    private final AuditLogService auditLogService;
    private final BranchAccessService branchAccessService;

    public FinalPayService(EmployeeRepository employeeRepository, ContractRepository contractRepository, EmployeeFinalPayRepository repository,
                           PayrollAdjustmentRepository adjustmentRepository, PayrollRunRepository runRepository, LeaveService leaveService,
                           HrSettingsService settingsService, StaffLoanService loanService, AuditLogService auditLogService, BranchAccessService branchAccessService) {
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.repository = repository;
        this.adjustmentRepository = adjustmentRepository;
        this.runRepository = runRepository;
        this.leaveService = leaveService;
        this.settingsService = settingsService;
        this.loanService = loanService;
        this.auditLogService = auditLogService;
        this.branchAccessService = branchAccessService;
    }

    /** What the final pay is now: the saved one, or a suggestion (unused leave only) when nothing was saved. */
    @Transactional(readOnly = true)
    public FinalPayDto get(Long employeeId) {
        assertCompanyWide();
        Employee e = employee(employeeId);
        return build(e, repository.findByEmployeeId(employeeId).orElse(null), null);
    }

    /** Works out the final pay from the given figures and puts it on the payroll of the last month (replacing what was put there before). */
    @Transactional
    public FinalPayDto save(Long employeeId, FinalPayRequest req) {
        assertCompanyWide();
        Employee e = employee(employeeId);
        if (req == null) throw ApiException.badRequest("Fill in the final pay.");
        EmployeeFinalPay existing = repository.findByEmployeeId(employeeId).orElse(null);
        FinalPayDto preview = build(e, existing, req);
        if (preview.blocked() != null) throw ApiException.conflict(preview.blocked());
        if (existing != null && runStatusOf(existing.getPeriodYear(), existing.getPeriodMonth()) != null
                && runStatusOf(existing.getPeriodYear(), existing.getPeriodMonth()) != PayrollStatus.DRAFT) {
            throw ApiException.conflict("The earlier final pay is on the " + YearMonth.of(existing.getPeriodYear(), existing.getPeriodMonth()) + " payroll, which is already approved. Reopen that payroll first.");
        }

        adjustmentRepository.deleteAll(adjustmentRepository.findByEmployeeIdAndSource(employeeId, SOURCE));
        adjustmentRepository.flush();
        for (FinalPayLineDto l : preview.lines()) {
            if (l.amount().signum() <= 0) continue;
            PayrollAdjustment a = new PayrollAdjustment();
            a.setEmployee(e);
            a.setKind(l.kind().equals("EARNING") ? AdjustmentKind.EARNING : AdjustmentKind.DEDUCTION);
            a.setName(l.name());
            a.setAmount(l.amount());
            a.setTaxable(l.kind().equals("EARNING"));
            a.setPeriodYear(preview.year());
            a.setPeriodMonth(preview.month());
            a.setNote("Final pay" + (l.days() != null && l.days().signum() > 0 ? " (" + l.days().stripTrailingZeros().toPlainString() + " days)" : ""));
            a.setSource(SOURCE);
            a.setCreatedByName(HrSupport.currentUserName());
            adjustmentRepository.save(a);
        }
        loanService.settleInMonth(employeeId, preview.settleLoans() ? preview.year() : null, preview.settleLoans() ? preview.month() : null);

        EmployeeFinalPay fp = existing != null ? existing : new EmployeeFinalPay();
        fp.setEmployee(e);
        fp.setPeriodYear(preview.year());
        fp.setPeriodMonth(preview.month());
        fp.setLastDay(preview.lastDay());
        fp.setDailyRate(preview.dailyRate());
        fp.setLeaveDays(preview.leaveDays());
        fp.setLeaveAmount(amountOf(preview, "Unused leave"));
        fp.setNoticePayDays(preview.noticePayDays());
        fp.setNoticePayAmount(amountOf(preview, "Pay in lieu of notice"));
        fp.setNoticeRecoverDays(preview.noticeRecoverDays());
        fp.setNoticeRecoverAmount(amountOf(preview, "Notice not served"));
        fp.setSettleLoans(preview.settleLoans());
        fp.setNote(HrSupport.blankToNull(req.note()));
        fp.setCreatedByName(HrSupport.currentUserName());
        repository.save(fp);
        auditLogService.record("FINAL_PAY_SAVED", "Final pay for " + e.fullName() + " put on the " + YearMonth.of(preview.year(), preview.month()) + " payroll (earnings " + preview.earnings()
                + ", deductions " + preview.deductions() + (preview.settleLoans() ? ", loans settled" : "") + ")", "Employee", employeeId);
        return build(e, fp, null);
    }

    /** Takes the final pay back off the payroll (the payroll must not be approved yet). */
    @Transactional
    public FinalPayDto remove(Long employeeId) {
        assertCompanyWide();
        Employee e = employee(employeeId);
        EmployeeFinalPay fp = repository.findByEmployeeId(employeeId).orElseThrow(() -> ApiException.notFound("No final pay was set for this employee."));
        PayrollStatus st = runStatusOf(fp.getPeriodYear(), fp.getPeriodMonth());
        if (st != null && st != PayrollStatus.DRAFT) {
            throw ApiException.conflict("The final pay is on the " + YearMonth.of(fp.getPeriodYear(), fp.getPeriodMonth()) + " payroll, which is already approved. Reopen that payroll first.");
        }
        adjustmentRepository.deleteAll(adjustmentRepository.findByEmployeeIdAndSource(employeeId, SOURCE));
        loanService.settleInMonth(employeeId, null, null);
        repository.delete(fp);
        repository.flush();
        auditLogService.record("FINAL_PAY_REMOVED", "Removed the final pay of " + e.fullName() + " from the " + YearMonth.of(fp.getPeriodYear(), fp.getPeriodMonth()) + " payroll", "Employee", employeeId);
        return build(e, null, null);
    }

    // ---- working it out ----

    private FinalPayDto build(Employee e, EmployeeFinalPay saved, FinalPayRequest req) {
        LocalDate last = e.getStatus() == EmployeeStatus.TERMINATED ? e.getTerminationDate() : null;
        String blocked = null;
        if (last == null) {
            blocked = "Record that " + e.fullName() + " left the company first (Employee left, on the employee page). Final pay is worked out from the last day.";
            last = LocalDate.now();
        }
        YearMonth ym = YearMonth.from(last);
        PayrollStatus runStatus = runStatusOf(ym.getYear(), ym.getMonthValue());
        if (blocked == null && runStatus != null && runStatus != PayrollStatus.DRAFT) {
            blocked = "The " + ym + " payroll is already " + runStatus.name().toLowerCase() + ". Reopen it (Payroll > that month > Reopen) before changing the final pay.";
        }

        // Daily rate the same way payroll works it out: base salary over the working days of the month.
        Contract contract = contractFor(e.getId(), last);
        BigDecimal base = contract != null ? contract.getBaseSalary() : BigDecimal.ZERO;
        Set<DayOfWeek> weekdays = settingsService.workingDays();
        int workingDays = LeaveService.countWorkingDays(ym.atDay(1), ym.atEndOfMonth(), weekdays, leaveService.holidaySet(ym.atDay(1), ym.atEndOfMonth()));
        BigDecimal rate = workingDays > 0 ? base.divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (blocked == null && contract == null) {
            blocked = e.fullName() + " has no contract, so there is no salary to work the final pay from.";
        }

        // Unused leave of the leaving year, for the types that are paid out.
        List<FinalPayLeaveDto> leave = new ArrayList<>();
        BigDecimal suggested = BigDecimal.ZERO;
        Set<Long> payable = leaveService.payoutTypeIds();
        for (LeaveBalanceDto b : leaveService.balancesOfEmployee(e, last.getYear())) {
            if (!payable.contains(b.leaveTypeId()) || b.unlimited() || b.remaining().signum() <= 0) continue;
            leave.add(new FinalPayLeaveDto(b.leaveTypeName(), b.remaining()));
            suggested = suggested.add(b.remaining());
        }
        BigDecimal leaveDays = days(req != null ? req.leaveDays() : null, saved != null && req == null ? saved.getLeaveDays() : null, suggested, "Unused leave days");
        BigDecimal noticePay = days(req != null ? req.noticePayDays() : null, saved != null && req == null ? saved.getNoticePayDays() : null, BigDecimal.ZERO, "Notice days paid");
        BigDecimal noticeRecover = days(req != null ? req.noticeRecoverDays() : null, saved != null && req == null ? saved.getNoticeRecoverDays() : null, BigDecimal.ZERO, "Notice days not served");

        List<FinalPayLineDto> lines = new ArrayList<>();
        BigDecimal earnings = BigDecimal.ZERO, deductions = BigDecimal.ZERO;
        if (leaveDays.signum() > 0 && rate.signum() > 0) {
            BigDecimal amount = rate.multiply(leaveDays).setScale(2, RoundingMode.HALF_UP);
            lines.add(new FinalPayLineDto("Unused leave payout", "EARNING", leaveDays, amount, "Taxable pay"));
            earnings = earnings.add(amount);
        }
        if (noticePay.signum() > 0 && rate.signum() > 0) {
            BigDecimal amount = rate.multiply(noticePay).setScale(2, RoundingMode.HALF_UP);
            lines.add(new FinalPayLineDto("Pay in lieu of notice", "EARNING", noticePay, amount, "Taxable pay"));
            earnings = earnings.add(amount);
        }
        if (noticeRecover.signum() > 0 && rate.signum() > 0) {
            BigDecimal amount = rate.multiply(noticeRecover).setScale(2, RoundingMode.HALF_UP);
            lines.add(new FinalPayLineDto("Notice not served", "DEDUCTION", noticeRecover, amount, "Taken from the net pay"));
            deductions = deductions.add(amount);
        }
        boolean settle = req != null && req.settleLoans() != null ? req.settleLoans() : saved != null && req == null && saved.isSettleLoans();
        List<FinalPayLoanDto> loans = new ArrayList<>();
        BigDecimal loansBalance = BigDecimal.ZERO;
        for (Due d : loanService.activeLoansOf(e.getId())) {
            loans.add(new FinalPayLoanDto(d.loan().getId(), d.loan().getLoanNumber(), d.balance()));
            loansBalance = loansBalance.add(d.balance());
        }
        return new FinalPayDto(e.getId(), e.getEmployeeNumber(), e.fullName(), e.getStatus() == EmployeeStatus.TERMINATED ? e.getTerminationDate() : null, ym.getYear(), ym.getMonthValue(),
                rate, base, workingDays, leaveDays, suggested, leave, noticePay, noticeRecover, settle, loans, loansBalance, lines, earnings, deductions,
                saved != null, runStatus, blocked, saved != null && req == null ? saved.getNote() : req != null ? HrSupport.blankToNull(req.note()) : null);
    }

    private static BigDecimal amountOf(FinalPayDto d, String prefix) {
        return d.lines().stream().filter(l -> l.name().startsWith(prefix)).map(FinalPayLineDto::amount).findFirst().orElse(BigDecimal.ZERO);
    }

    private static BigDecimal days(BigDecimal given, BigDecimal saved, BigDecimal fallback, String what) {
        BigDecimal v = given != null ? given : saved != null ? saved : fallback;
        if (v.signum() < 0) throw ApiException.badRequest(what + " can't be negative.");
        if (v.compareTo(MAX_DAYS) > 0) throw ApiException.badRequest(what + " can't be more than 366.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private Contract contractFor(Long employeeId, LocalDate day) {
        Contract fallback = null;
        for (Contract c : contractRepository.findByEmployeeIdOrderByStartDateDesc(employeeId)) {
            if (c.getStatus() == ContractStatus.DRAFT) continue;
            if (fallback == null) fallback = c;
            LocalDate end = ContractService.effectiveEnd(c);
            if (!c.getStartDate().isAfter(day) && (end == null || !end.isBefore(day))) return c;
        }
        return fallback;
    }

    private PayrollStatus runStatusOf(int year, int month) {
        return runRepository.findByPeriodYearAndPeriodMonth(year, month).map(PayrollRun::getStatus).orElse(null);
    }

    private Employee employee(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
    }

    private void assertCompanyWide() {
        if (branchAccessService.isRestricted()) {
            throw ApiException.forbidden("Payroll covers all branches — it's only available to users who aren't limited to certain branches.");
        }
    }
}
