package com.posapp.hr;

import com.posapp.common.ExportFiles;
import com.posapp.hr.PayrollExtrasDtos.AdjustmentDto;
import com.posapp.hr.PayrollExtrasDtos.LoanDto;
import com.posapp.hr.PayrollExtrasDtos.RemittanceDto;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.reports.ReportExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel / PDF / CSV downloads of the HR lists. Each takes the filters of its screen and returns the whole filtered list, not one page.
 * The rows come from the list endpoints themselves (called through their Spring proxies), so the filters, the branch rule and the
 * access rule of each screen apply here unchanged. The paths sit under the module paths, so the module license gate covers them.
 */
@RestController
@RequestMapping("/api/hr")
public class HrExportController {

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    private final HrController hr;
    private final ContractController contracts;
    private final PayrollController payroll;
    private final PayrollExtrasController extras;
    private final ReportExportService exportService;

    public HrExportController(HrController hr, ContractController contracts, PayrollController payroll, PayrollExtrasController extras, ReportExportService exportService) {
        this.extras = extras;
        this.hr = hr;
        this.contracts = contracts;
        this.payroll = payroll;
        this.exportService = exportService;
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String num(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static <T> List<T> all(ResponseEntity<List<T>> r) {
        return r.getBody() == null ? List.of() : r.getBody();
    }

    private static String title(String name, String... filters) {
        List<String> bits = new ArrayList<>();
        for (String f : filters) {
            if (f != null && !f.isBlank()) {
                bits.add(f);
            }
        }
        return bits.isEmpty() ? name : name + " (" + String.join(", ", bits) + ")";
    }

    private static String search(String q) {
        return q == null || q.isBlank() ? null : "search \"" + q.trim() + "\"";
    }

    private static String range(LocalDate from, LocalDate to) {
        return from == null && to == null ? null : (from == null ? "…" : from.toString()) + " to " + (to == null ? "…" : to.toString());
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HR_VIEW')")
    @GetMapping("/employees/export")
    public ResponseEntity<byte[]> employees(@RequestParam(required = false) EmployeeStatus status, @RequestParam(required = false) Long departmentId,
                                            @RequestParam(required = false) Long branchId, @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "xlsx") String format) {
        List<EmployeeDto> rows = all(hr.employees(status, departmentId, branchId, q, null, null));
        List<String> h = List.of("No.", "Name", "Job title", "Department", "Reports to", "Branch", "Phone", "E-mail", "Type", "Hired", "Left", "Status");
        List<List<String>> body = new ArrayList<>();
        for (EmployeeDto e : rows) {
            body.add(List.of(s(e.employeeNumber()), s(e.fullName()), s(e.jobTitle()), s(e.departmentName()), s(e.managerName()), s(e.branchName()), s(e.phone()), s(e.email()),
                    s(e.employmentType()), s(e.hireDate()), s(e.terminationDate()), s(e.status())));
        }
        return ExportFiles.file(exportService, title("Employees", status == null ? null : "status " + status, search(q)), "employees", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HR_VIEW')")
    @GetMapping("/leaves/export")
    public ResponseEntity<byte[]> leaves(@RequestParam(required = false) LeaveStatus status, @RequestParam(required = false) Long employeeId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String q, @RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        List<LeaveRequestDto> rows = all(hr.leaves(status, employeeId, from, to, q, branchId, null, null));
        List<String> h = List.of("Employee", "No.", "Leave type", "Paid", "From", "To", "Days", "Reason", "Status", "Decided by", "Decision note");
        List<List<String>> body = new ArrayList<>();
        for (LeaveRequestDto r : rows) {
            body.add(List.of(s(r.employeeName()), s(r.employeeNumber()), s(r.leaveTypeName()), r.paid() ? "Yes" : "No", s(r.startDate()), s(r.endDate()), s(r.days()),
                    s(r.reason()), s(r.status()), s(r.decidedBy()), s(r.decisionNote())));
        }
        return ExportFiles.file(exportService, title("Leave requests", status == null ? null : "status " + status, range(from, to), search(q)), "leave-requests", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HR_VIEW')")
    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> attendance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Long employeeId, @RequestParam(required = false) Long departmentId,
                                             @RequestParam(required = false) AttendanceStatus status, @RequestParam(required = false) String q,
                                             @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "xlsx") String format) {
        List<AttendanceDto> rows = all(hr.attendance(from, to, employeeId, departmentId, status, q, branchId, null, null));
        List<String> h = List.of("Date", "Employee", "No.", "In", "Out", "Hours", "Overtime", "Status", "Note");
        List<List<String>> body = new ArrayList<>();
        for (AttendanceDto a : rows) {
            body.add(List.of(s(a.workDate()), s(a.employeeName()), s(a.employeeNumber()), s(a.clockIn()), s(a.clockOut()), num(a.hoursWorked()), num(a.overtimeHours()), s(a.status()), s(a.note())));
        }
        return ExportFiles.file(exportService, title("Attendance", range(from, to), status == null ? null : "status " + status, search(q)), "attendance", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
    @GetMapping("/contracts/export")
    public ResponseEntity<byte[]> contracts(@RequestParam(required = false) ContractStatus status, @RequestParam(required = false) Long employeeId,
                                            @RequestParam(required = false) ContractType type, @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "xlsx") String format) {
        List<ContractDto> rows = all(contracts.list(status, employeeId, type, q, branchId, null, null));
        List<String> h = List.of("Contract #", "Employee", "No.", "Type", "Status", "From", "To", "Job title", "Base salary", "Extra items");
        List<List<String>> body = new ArrayList<>();
        for (ContractDto c : rows) {
            body.add(List.of(s(c.contractNumber()), s(c.employeeName()), s(c.employeeNumber()), s(c.type()), s(c.status()), s(c.startDate()), s(c.endDate()), s(c.jobTitle()),
                    num(c.baseSalary()), String.valueOf(c.components().size())));
        }
        return ExportFiles.file(exportService, title("Contracts", status == null ? null : "status " + status, type == null ? null : "type " + type, search(q)), "contracts", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
    @GetMapping("/payroll/runs/export")
    public ResponseEntity<byte[]> payrollRuns(@RequestParam(required = false) PayrollStatus status, @RequestParam(required = false) Integer year,
                                              @RequestParam(defaultValue = "xlsx") String format) {
        List<PayrollRunDto> rows = all(payroll.runs(status, year, null, null));
        List<String> h = List.of("Month", "Employees", "Gross", "PAYE", "Employee contributions", "Other deductions", "Net pay", "Employer contributions", "Status", "Paid on");
        List<List<String>> body = new ArrayList<>();
        for (PayrollRunDto r : rows) {
            body.add(List.of(MONTHS[r.periodMonth() - 1] + " " + r.periodYear(), String.valueOf(r.employeeCount()), num(r.totalGross()), num(r.totalPaye()),
                    num(r.totalEmployeeContributions()), num(r.totalOtherDeductions()), num(r.totalNet()), num(r.totalEmployerContributions()), s(r.status()), s(r.paidDate())));
        }
        return ExportFiles.file(exportService, title("Payroll runs", status == null ? null : "status " + status, year == null ? null : "year " + year), "payroll-runs", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
    @GetMapping("/payroll/adjustments/export")
    public ResponseEntity<byte[]> adjustments(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month, @RequestParam(required = false) Long employeeId,
                                              @RequestParam(required = false) AdjustmentKind kind, @RequestParam(required = false) String q, @RequestParam(defaultValue = "xlsx") String format) {
        List<AdjustmentDto> rows = all(extras.adjustments(year, month, employeeId, kind, q, null, null));
        List<String> h = List.of("Month", "Employee", "No.", "Adds / takes", "Name", "Amount", "Taxable", "Status", "Note", "Entered by");
        List<List<String>> body = new ArrayList<>();
        for (AdjustmentDto a : rows) {
            body.add(List.of(MONTHS[a.month() - 1] + " " + a.year(), s(a.employeeName()), s(a.employeeNumber()), a.kind() == AdjustmentKind.EARNING ? "Adds" : "Takes", s(a.name()), num(a.amount()),
                    a.kind() == AdjustmentKind.EARNING ? (a.taxable() ? "Yes" : "No") : "", s(a.status()), s(a.note()), s(a.createdByName())));
        }
        return ExportFiles.file(exportService, title("Payroll adjustments", year == null ? null : "year " + year, month == null ? null : MONTHS[Math.max(1, Math.min(12, month)) - 1], kind == null ? null : kind.name().toLowerCase(), search(q)), "payroll-adjustments", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
    @GetMapping("/payroll/loans/export")
    public ResponseEntity<byte[]> loans(@RequestParam(required = false) LoanStatus status, @RequestParam(required = false) Long employeeId, @RequestParam(required = false) LoanType type,
                                        @RequestParam(required = false) String q, @RequestParam(defaultValue = "xlsx") String format) {
        List<LoanDto> rows = all(extras.loans(status, employeeId, type, q, null, null));
        List<String> h = List.of("Loan #", "Employee", "No.", "Type", "Given on", "Amount", "Installment", "First deduction", "Repaid", "Balance", "Status");
        List<List<String>> body = new ArrayList<>();
        for (LoanDto l : rows) {
            body.add(List.of(s(l.loanNumber()), s(l.employeeName()), s(l.employeeNumber()), l.loanType() == LoanType.ADVANCE ? "Advance" : "Loan", s(l.issueDate()), num(l.principal()), num(l.installment()),
                    MONTHS[l.firstMonth() - 1] + " " + l.firstYear(), num(l.repaid()), num(l.balance()), s(l.status())));
        }
        return ExportFiles.file(exportService, title("Staff advances and loans", status == null ? null : "status " + status, type == null ? null : "type " + type, search(q)), "staff-loans", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
    @GetMapping("/payroll/remittances/export")
    public ResponseEntity<byte[]> remittances(@RequestParam(required = false) RemittanceKind kind, @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                                              @RequestParam(required = false) String status, @RequestParam(required = false) String q, @RequestParam(defaultValue = "xlsx") String format) {
        List<RemittanceDto> rows = all(extras.remittances(kind, year, month, status, q, null, null));
        List<String> h = List.of("Paid on", "What", "Payroll month", "Amount", "Paid to", "Reference", "Note", "Status");
        List<List<String>> body = new ArrayList<>();
        for (RemittanceDto r : rows) {
            body.add(List.of(s(r.paidOn()), s(r.kindLabel()), MONTHS[r.month() - 1] + " " + r.year(), num(r.amount()), s(r.paidTo()), s(r.reference()), s(r.note()), s(r.status())));
        }
        return ExportFiles.file(exportService, title("Tax and social-security payments", kind == null ? null : kind.name(), year == null ? null : "year " + year, search(q)), "payroll-remittances", h, body, format);
    }
}
