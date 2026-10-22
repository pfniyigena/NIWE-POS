package com.posapp.hr.dto;

import com.posapp.hr.EmployeeStatus;
import com.posapp.hr.PayrollStatus;
import com.posapp.hr.dto.HrDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What an employee sees of their own record (employee self-service). Never carries other people's data or company totals. */
public final class MyHrDtos {

    private MyHrDtos() {
    }

    public record MyProfileDto(Long id, String employeeNumber, String fullName, String jobTitle, String departmentName, String managerName,
                               LocalDate hireDate, EmployeeStatus status, String phone, String email, String address,
                               String emergencyContactName, String emergencyContactPhone) {
    }

    /** {@code linked} false = the login is not tied to an employee record yet (HR does that on the employee page). */
    public record MyOverviewDto(boolean linked, MyProfileDto profile, int year, List<LeaveBalanceDto> balances, int pendingRequests,
                                int payslipCount, TodayDto today) {
    }

    public record MyPayslipRowDto(Long runId, int year, int month, PayrollStatus status, LocalDate paidDate, BigDecimal grossPay,
                                  BigDecimal payeTax, BigDecimal employeeContributions, BigDecimal otherDeductions, BigDecimal netPay) {
    }

    public record MyPayslipDto(Long runId, int year, int month, PayrollStatus status, LocalDate paidDate, PayrollLineDto line,
                               List<PayrollItemDto> items) {
    }

    public record MyContactRequest(String phone, String email, String address, String emergencyContactName, String emergencyContactPhone) {
    }
}
