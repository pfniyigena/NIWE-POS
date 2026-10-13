package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Row types of the HR reports (HR > Reports), used by {@code HrReportService} and the matching {@code ReportController} endpoints. */
public final class HrReportRows {

    private HrReportRows() {
    }

    public record EmployeeDirectoryRow(String employeeNumber, String name, String jobTitle, String department, String manager, String branch,
                                       String status, LocalDate hireDate, String phone, String email) {
    }

    public record HeadcountRow(String department, long active, long inactive, long left, long total) {
    }

    public record LeaveBalanceRow(String employeeNumber, String employee, String leaveType, String entitled, BigDecimal used, BigDecimal pending,
                                  String remaining) {
    }

    public record LeaveRequestRow(String employeeNumber, String employee, String leaveType, LocalDate startDate, LocalDate endDate, BigDecimal days,
                                  String status, String decidedBy, String reason) {
    }

    public record AttendanceSummaryRow(String employeeNumber, String employee, String department, int expectedDays, int presentDays, int lateDays,
                                       int absentDays, BigDecimal leaveDays, BigDecimal hoursWorked, BigDecimal overtimeHours) {
    }

    public record ContractExpiryRow(String contractNumber, String employee, String type, LocalDate startDate, LocalDate endDate, Integer daysLeft) {
    }

    public record PayrollRegisterRow(String period, String employeeNumber, String employee, String department, BigDecimal gross, BigDecimal paye,
                                     BigDecimal employeeContributions, BigDecimal otherDeductions, BigDecimal net,
                                     BigDecimal employerContributions, String status) {
    }

    public record PayrollSummaryRow(String period, String status, int employees, BigDecimal gross, BigDecimal paye,
                                    BigDecimal employeeContributions, BigDecimal otherDeductions, BigDecimal net,
                                    BigDecimal employerContributions, BigDecimal totalCost) {
    }
}
