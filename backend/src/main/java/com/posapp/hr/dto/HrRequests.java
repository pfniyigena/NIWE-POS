package com.posapp.hr.dto;

import com.posapp.hr.AttendanceStatus;
import com.posapp.hr.ComponentKind;
import com.posapp.hr.ContractType;
import com.posapp.hr.EmploymentType;
import com.posapp.hr.PayableTarget;
import com.posapp.hr.RuleBase;
import com.posapp.hr.RuleKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class HrRequests {

    private HrRequests() {
    }

    public record DepartmentRequest(@NotBlank String name, String code, Long parentId, Long managerId, Boolean active) {
    }

    public record PositionRequest(@NotBlank String title, Long departmentId, String description, Integer plannedHeadcount, Boolean active) {
    }

    public record EmployeeRequest(String employeeNumber, @NotBlank String firstName, @NotBlank String lastName, LocalDate dateOfBirth,
                                  String nationalId, String phone, String email, String address, String emergencyContactName,
                                  String emergencyContactPhone, @NotNull LocalDate hireDate, EmploymentType employmentType,
                                  String jobTitle, Long departmentId, Long positionId, Long managerId, Long branchId, Long userId,
                                  String bankName, String bankAccount, String taxNumber, String socialSecurityNumber,
                                  Boolean attendanceTracked, String notes) {
    }

    public record TerminateRequest(@NotNull LocalDate date, String reason) {
    }

    public record LeaveTypeRequest(@NotBlank String name, Integer daysPerYear, Boolean paid, Boolean active) {
    }

    public record HolidayRequest(@NotNull LocalDate date, @NotBlank String name) {
    }

    /** {@code employeeId} empty = the signed-in user's own employee record. */
    public record LeaveRequestRequest(Long employeeId, @NotNull Long leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                      String reason) {
    }

    public record DecisionRequest(String note) {
    }

    public record AttendanceRequest(@NotNull Long employeeId, @NotNull LocalDate workDate, LocalTime clockIn, LocalTime clockOut,
                                    AttendanceStatus status, String note) {
    }

    public record SettingsRequest(LocalTime workStart, Integer graceMinutes, BigDecimal standardDailyHours, String workingDays,
                                  BigDecimal overtimeMultiplier, Boolean taxBandsAnnual) {
    }

    public record ComponentRequest(@NotBlank String name, ComponentKind kind, @NotNull BigDecimal amount, Boolean taxable) {
    }

    public record ContractRequest(@NotNull Long employeeId, ContractType type, @NotNull LocalDate startDate, LocalDate endDate,
                                  String jobTitle, @NotNull BigDecimal baseSalary, String notes, List<ComponentRequest> components) {
    }

    public record ContractTerminateRequest(@NotNull LocalDate date, String reason) {
    }

    public record RenewRequest(@NotNull LocalDate startDate, LocalDate endDate, BigDecimal baseSalary) {
    }

    public record RuleRequest(String code, @NotBlank String name, @NotNull RuleKind kind, RuleBase base, @NotNull BigDecimal ratePercent,
                              BigDecimal capAmount, Boolean deductibleFromTax, PayableTarget target, Boolean active, Integer sortOrder) {
    }

    public record BandRequest(@NotNull BigDecimal fromAmount, BigDecimal toAmount, @NotNull BigDecimal ratePercent) {
    }

    public record BandsRequest(List<BandRequest> bands) {
    }

    public record GenerateRequest(@NotNull Integer year, @NotNull Integer month) {
    }

    public record PayRunRequest(LocalDate paidDate) {
    }
}
