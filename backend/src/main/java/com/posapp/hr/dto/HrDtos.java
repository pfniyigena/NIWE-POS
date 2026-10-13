package com.posapp.hr.dto;

import com.posapp.hr.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class HrDtos {

    private HrDtos() {
    }

    public record EmployeeDto(Long id, String employeeNumber, String firstName, String lastName, String fullName, LocalDate dateOfBirth,
                              String nationalId, String phone, String email, String address, String emergencyContactName,
                              String emergencyContactPhone, LocalDate hireDate, LocalDate terminationDate, String terminationReason,
                              EmployeeStatus status, EmploymentType employmentType, String jobTitle, Long departmentId,
                              String departmentName, Long positionId, String positionTitle, Long managerId, String managerName,
                              Long branchId, String branchName, Long userId, String userLabel, String bankName, String bankAccount,
                              String taxNumber, String socialSecurityNumber, boolean attendanceTracked, String notes) {
        public static EmployeeDto from(Employee e, String branchName, String userLabel) {
            return new EmployeeDto(e.getId(), e.getEmployeeNumber(), e.getFirstName(), e.getLastName(), e.fullName(), e.getDateOfBirth(),
                    e.getNationalId(), e.getPhone(), e.getEmail(), e.getAddress(), e.getEmergencyContactName(),
                    e.getEmergencyContactPhone(), e.getHireDate(), e.getTerminationDate(), e.getTerminationReason(), e.getStatus(),
                    e.getEmploymentType(), e.getJobTitle(),
                    e.getDepartment() != null ? e.getDepartment().getId() : null, e.getDepartment() != null ? e.getDepartment().getName() : null,
                    e.getPosition() != null ? e.getPosition().getId() : null, e.getPosition() != null ? e.getPosition().getTitle() : null,
                    e.getManager() != null ? e.getManager().getId() : null, e.getManager() != null ? e.getManager().fullName() : null,
                    e.getBranchId(), branchName, e.getUserId(), userLabel, e.getBankName(), e.getBankAccount(), e.getTaxNumber(),
                    e.getSocialSecurityNumber(), e.isAttendanceTracked(), e.getNotes());
        }
    }

    public record DepartmentDto(Long id, String name, String code, Long parentId, String parentName, Long managerId, String managerName,
                                boolean active, long employeeCount) {
        public static DepartmentDto from(Department d, long employeeCount) {
            return new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getParent() != null ? d.getParent().getId() : null,
                    d.getParent() != null ? d.getParent().getName() : null, d.getManager() != null ? d.getManager().getId() : null,
                    d.getManager() != null ? d.getManager().fullName() : null, d.isActive(), employeeCount);
        }
    }

    public record PositionDto(Long id, String title, Long departmentId, String departmentName, String description, int plannedHeadcount,
                              boolean active, long filled) {
        public static PositionDto from(Position p, long filled) {
            return new PositionDto(p.getId(), p.getTitle(), p.getDepartment() != null ? p.getDepartment().getId() : null,
                    p.getDepartment() != null ? p.getDepartment().getName() : null, p.getDescription(), p.getPlannedHeadcount(),
                    p.isActive(), filled);
        }
    }

    /** A department in the org chart; people are the active employees in it directly. */
    public record DeptNode(Long id, String name, String managerName, long employeeCount, List<DeptNode> children) {
    }

    /** A person in the reporting tree. */
    public record PersonNode(Long id, String name, String jobTitle, String departmentName, List<PersonNode> reports) {
    }

    public record OrgChartDto(List<DeptNode> departments, List<PersonNode> people) {
    }

    public record LeaveTypeDto(Long id, String name, int daysPerYear, boolean paid, boolean active) {
        public static LeaveTypeDto from(LeaveType t) {
            return new LeaveTypeDto(t.getId(), t.getName(), t.getDaysPerYear(), t.isPaid(), t.isActive());
        }
    }

    public record HolidayDto(Long id, LocalDate date, String name) {
        public static HolidayDto from(PublicHoliday h) {
            return new HolidayDto(h.getId(), h.getHolidayDate(), h.getName());
        }
    }

    public record LeaveRequestDto(Long id, Long employeeId, String employeeNumber, String employeeName, Long leaveTypeId,
                                  String leaveTypeName, boolean paid, LocalDate startDate, LocalDate endDate, BigDecimal days,
                                  String reason, LeaveStatus status, String decidedBy, Instant decidedAt, String decisionNote,
                                  Instant createdAt) {
        public static LeaveRequestDto from(LeaveRequest r) {
            return new LeaveRequestDto(r.getId(), r.getEmployee().getId(), r.getEmployee().getEmployeeNumber(), r.getEmployee().fullName(),
                    r.getLeaveType().getId(), r.getLeaveType().getName(), r.getLeaveType().isPaid(), r.getStartDate(), r.getEndDate(),
                    r.getDays(), r.getReason(), r.getStatus(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote(), r.getCreatedAt());
        }
    }

    /** {@code entitled} 0 with {@code unlimited} true means the leave type has no yearly limit. */
    public record LeaveBalanceDto(Long employeeId, String employeeNumber, String employeeName, Long leaveTypeId, String leaveTypeName,
                                  boolean unlimited, BigDecimal entitled, BigDecimal used, BigDecimal pending, BigDecimal remaining) {
    }

    public record AttendanceDto(Long id, Long employeeId, String employeeNumber, String employeeName, LocalDate workDate, LocalTime clockIn,
                                LocalTime clockOut, AttendanceStatus status, BigDecimal hoursWorked, BigDecimal overtimeHours, String note) {
        public static AttendanceDto from(AttendanceRecord a) {
            return new AttendanceDto(a.getId(), a.getEmployee().getId(), a.getEmployee().getEmployeeNumber(), a.getEmployee().fullName(),
                    a.getWorkDate(), a.getClockIn(), a.getClockOut(), a.getStatus(), a.getHoursWorked(), a.getOvertimeHours(), a.getNote());
        }
    }

    public record AttendanceSummaryDto(Long employeeId, String employeeNumber, String employeeName, String departmentName, int expectedDays,
                                       int presentDays, int lateDays, int absentDays, BigDecimal leaveDays, BigDecimal hoursWorked,
                                       BigDecimal overtimeHours) {
    }

    /** What the signed-in user sees on the clock-in card. {@code linked} false = no employee record is linked to their login. */
    public record TodayDto(boolean linked, Long employeeId, String employeeName, AttendanceDto record) {
    }

    public record SettingsDto(LocalTime workStart, int graceMinutes, BigDecimal standardDailyHours, String workingDays,
                              BigDecimal overtimeMultiplier, boolean taxBandsAnnual, String payrollCountry) {
    }

    public record ComponentDto(Long id, String name, ComponentKind kind, BigDecimal amount, boolean taxable) {
        public static ComponentDto from(ContractComponent c) {
            return new ComponentDto(c.getId(), c.getName(), c.getKind(), c.getAmount(), c.isTaxable());
        }
    }

    public record ContractDto(Long id, String contractNumber, Long employeeId, String employeeNumber, String employeeName, ContractType type,
                              ContractStatus status, LocalDate startDate, LocalDate endDate, String jobTitle, BigDecimal baseSalary,
                              LocalDate terminationDate, String terminationReason, String renewsContractNumber, String notes,
                              Integer daysToExpiry, List<ComponentDto> components) {
        public static ContractDto from(Contract c, Integer daysToExpiry) {
            return new ContractDto(c.getId(), c.getContractNumber(), c.getEmployee().getId(), c.getEmployee().getEmployeeNumber(),
                    c.getEmployee().fullName(), c.getType(), c.getStatus(), c.getStartDate(), c.getEndDate(), c.getJobTitle(),
                    c.getBaseSalary(), c.getTerminationDate(), c.getTerminationReason(), c.getRenewsContractNumber(), c.getNotes(),
                    daysToExpiry, c.getComponents().stream().map(ComponentDto::from).toList());
        }
    }

    public record RuleDto(Long id, String code, String name, RuleKind kind, RuleBase base, BigDecimal ratePercent, BigDecimal capAmount,
                          boolean deductibleFromTax, PayableTarget target, boolean active, int sortOrder) {
        public static RuleDto from(PayrollRule r) {
            return new RuleDto(r.getId(), r.getCode(), r.getName(), r.getKind(), r.getBase(), r.getRatePercent(), r.getCapAmount(),
                    r.isDeductibleFromTax(), r.getTarget(), r.isActive(), r.getSortOrder());
        }
    }

    public record BandDto(Long id, BigDecimal fromAmount, BigDecimal toAmount, BigDecimal ratePercent) {
        public static BandDto from(TaxBand b) {
            return new BandDto(b.getId(), b.getFromAmount(), b.getToAmount(), b.getRatePercent());
        }
    }

    public record PayrollRunDto(Long id, int periodYear, int periodMonth, PayrollStatus status, int employeeCount, BigDecimal totalGross,
                                BigDecimal totalPaye, BigDecimal totalEmployeeContributions, BigDecimal totalOtherDeductions,
                                BigDecimal totalNet, BigDecimal totalEmployerContributions, String createdByName, String approvedByName,
                                Instant approvedAt, LocalDate paidDate, String note) {
        public static PayrollRunDto from(PayrollRun r) {
            return new PayrollRunDto(r.getId(), r.getPeriodYear(), r.getPeriodMonth(), r.getStatus(), r.getLines().size(), r.getTotalGross(),
                    r.getTotalPaye(), r.getTotalEmployeeContributions(), r.getTotalOtherDeductions(), r.getTotalNet(),
                    r.getTotalEmployerContributions(), r.getCreatedByName(), r.getApprovedByName(), r.getApprovedAt(), r.getPaidDate(),
                    r.getNote());
        }
    }

    public record PayrollLineDto(Long id, Long employeeId, String employeeNumber, String employeeName, String departmentName, String contractNumber,
                                 Long branchId, String bankName, String bankAccount, BigDecimal baseSalary, int daysInMonth, int payableDays, BigDecimal unpaidLeaveDays,
                                 BigDecimal absentDays, BigDecimal overtimeHours, BigDecimal grossPay, BigDecimal taxablePay,
                                 BigDecimal payeTax, BigDecimal employeeContributions, BigDecimal otherDeductions, BigDecimal netPay,
                                 BigDecimal employerContributions) {
        public static PayrollLineDto from(PayrollLine l) {
            return new PayrollLineDto(l.getId(), l.getEmployeeId(), l.getEmployeeNumber(), l.getEmployeeName(), l.getDepartmentName(),
                    l.getContractNumber(), l.getBranchId(), l.getBankName(), l.getBankAccount(), l.getBaseSalary(), l.getDaysInMonth(), l.getPayableDays(), l.getUnpaidLeaveDays(),
                    l.getAbsentDays(), l.getOvertimeHours(), l.getGrossPay(), l.getTaxablePay(), l.getPayeTax(), l.getEmployeeContributions(),
                    l.getOtherDeductions(), l.getNetPay(), l.getEmployerContributions());
        }
    }

    public record PayrollItemDto(ItemKind kind, String name, BigDecimal amount, boolean taxable) {
        public static PayrollItemDto from(PayrollItem i) {
            return new PayrollItemDto(i.getKind(), i.getName(), i.getAmount(), i.isTaxable());
        }
    }

    public record PayrollRunDetailDto(PayrollRunDto run, List<PayrollLineDto> lines, List<String> warnings) {
    }

    public record PayslipDto(PayrollRunDto run, PayrollLineDto line, String bankName, String bankAccount, List<PayrollItemDto> items) {
    }

    /** Result of emailing payslips: how many went out, who has no email address, and who failed (with the reason). */
    public record PayslipEmailResultDto(int sent, List<String> noEmail, List<String> failed) {
    }
}
