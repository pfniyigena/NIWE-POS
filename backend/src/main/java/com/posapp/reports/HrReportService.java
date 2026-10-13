package com.posapp.reports;

import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.*;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.reports.dto.HrReportRows.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** The HR reports. Salary figures only come from the payroll ones. */
@Service
public class HrReportService {

    private final EmployeeRepository employeeRepository;
    private final PayrollRunRepository runRepository;
    private final LeaveService leaveService;
    private final AttendanceService attendanceService;
    private final ContractService contractService;
    private final HrSupport support;
    private final BranchAccessService branchAccessService;

    public HrReportService(EmployeeRepository employeeRepository, PayrollRunRepository runRepository, LeaveService leaveService,
                           AttendanceService attendanceService, ContractService contractService, HrSupport support,
                           BranchAccessService branchAccessService) {
        this.employeeRepository = employeeRepository;
        this.runRepository = runRepository;
        this.leaveService = leaveService;
        this.attendanceService = attendanceService;
        this.contractService = contractService;
        this.support = support;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDirectoryRow> directory(EmployeeStatus status, Long departmentId) {
        Map<Long, String> branches = support.branchNames();
        List<EmployeeDirectoryRow> rows = new ArrayList<>();
        for (Employee e : employeeRepository.findAllByOrderByFirstNameAscLastNameAsc()) {
            if ((status != null && e.getStatus() != status)
                    || (departmentId != null && (e.getDepartment() == null || !e.getDepartment().getId().equals(departmentId)))
                    || !branchAccessService.canAccess(e.getBranchId())) {
                continue;
            }
            rows.add(new EmployeeDirectoryRow(e.getEmployeeNumber(), e.fullName(), e.getJobTitle(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null, e.getManager() != null ? e.getManager().fullName() : null,
                    e.getBranchId() != null ? branches.get(e.getBranchId()) : null, e.getStatus().name(), e.getHireDate(), e.getPhone(), e.getEmail()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<HeadcountRow> headcount() {
        Map<String, long[]> byDept = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Employee e : employeeRepository.findAll()) {
            if (!branchAccessService.canAccess(e.getBranchId())) {
                continue;
            }
            long[] c = byDept.computeIfAbsent(e.getDepartment() != null ? e.getDepartment().getName() : "(No department)", k -> new long[3]);
            switch (e.getStatus()) {
                case ACTIVE -> c[0]++;
                case INACTIVE -> c[1]++;
                case TERMINATED -> c[2]++;
            }
        }
        List<HeadcountRow> rows = new ArrayList<>();
        byDept.forEach((name, c) -> rows.add(new HeadcountRow(name, c[0], c[1], c[2], c[0] + c[1] + c[2])));
        return rows;
    }

    @Transactional
    public List<LeaveBalanceRow> leaveBalances(int year) {
        return leaveService.balances(year, null).stream()
                .map(b -> new LeaveBalanceRow(b.employeeNumber(), b.employeeName(), b.leaveTypeName(),
                        b.unlimited() ? "No limit" : b.entitled().stripTrailingZeros().toPlainString(), b.used(), b.pending(),
                        b.unlimited() ? "—" : b.remaining().stripTrailingZeros().toPlainString())).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestRow> leaveRequests(LocalDate from, LocalDate to) {
        return leaveService.listRequests(null, null, from, to).stream()
                .map(r -> new LeaveRequestRow(r.employeeNumber(), r.employeeName(), r.leaveTypeName(), r.startDate(), r.endDate(), r.days(),
                        r.status().name(), r.decidedBy(), r.reason())).toList();
    }

    @Transactional
    public List<AttendanceSummaryRow> attendance(LocalDate from, LocalDate to) {
        return attendanceService.summary(from, to, null, null).stream()
                .map(s -> new AttendanceSummaryRow(s.employeeNumber(), s.employeeName(), s.departmentName(), s.expectedDays(), s.presentDays(),
                        s.lateDays(), s.absentDays(), s.leaveDays(), s.hoursWorked(), s.overtimeHours())).toList();
    }

    @Transactional(readOnly = true)
    public List<ContractExpiryRow> contractsExpiring(int days) {
        return contractService.expiring(days).stream()
                .map(c -> new ContractExpiryRow(c.contractNumber(), c.employeeName(), c.type().name(), c.startDate(), c.endDate(), c.daysToExpiry())).toList();
    }

    @Transactional(readOnly = true)
    public List<PayrollRegisterRow> payrollRegister(LocalDate from, LocalDate to) {
        assertCompanyWide();
        List<PayrollRegisterRow> rows = new ArrayList<>();
        for (PayrollRun run : runsBetween(from, to)) {
            String period = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).toString();
            run.getLines().stream().sorted(Comparator.comparing(PayrollLine::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .forEach(l -> rows.add(new PayrollRegisterRow(period, l.getEmployeeNumber(), l.getEmployeeName(), l.getDepartmentName(), l.getGrossPay(),
                            l.getPayeTax(), l.getEmployeeContributions(), l.getOtherDeductions(), l.getNetPay(), l.getEmployerContributions(),
                            run.getStatus().name())));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PayrollSummaryRow> payrollSummary(LocalDate from, LocalDate to) {
        assertCompanyWide();
        return runsBetween(from, to).stream().map(r -> new PayrollSummaryRow(YearMonth.of(r.getPeriodYear(), r.getPeriodMonth()).toString(),
                r.getStatus().name(), r.getLines().size(), r.getTotalGross(), r.getTotalPaye(), r.getTotalEmployeeContributions(),
                r.getTotalOtherDeductions(), r.getTotalNet(), r.getTotalEmployerContributions(),
                r.getTotalGross().add(r.getTotalEmployerContributions()))).toList();
    }

    private List<PayrollRun> runsBetween(LocalDate from, LocalDate to) {
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        return runRepository.findAllByOrderByPeriodYearDescPeriodMonthDesc().stream()
                .filter(r -> {
                    YearMonth p = YearMonth.of(r.getPeriodYear(), r.getPeriodMonth());
                    return !p.isBefore(start) && !p.isAfter(end);
                })
                .sorted(Comparator.comparing((PayrollRun r) -> r.getPeriodYear() * 100 + r.getPeriodMonth())).toList();
    }

    private void assertCompanyWide() {
        if (branchAccessService.isRestricted()) {
            throw ApiException.forbidden("Payroll reports cover all branches — they're only available to users who aren't limited to certain branches.");
        }
    }
}
