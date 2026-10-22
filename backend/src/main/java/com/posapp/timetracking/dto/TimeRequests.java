package com.posapp.timetracking.dto;

import com.posapp.timetracking.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Bodies of the time endpoints. */
public final class TimeRequests {

    private TimeRequests() {
    }

    public record ProjectRequest(String name, String code, Long customerId, String description, ProjectStatus status, Boolean billable,
                                 BigDecimal defaultRate, BigDecimal budgetHours, LocalDate startDate, LocalDate endDate, Long branchId) {
    }

    public record TaskRequest(String name, BigDecimal estimatedHours, Boolean active) {
    }

    public record RateRequest(Long userId, BigDecimal hourlyRate) {
    }

    /** {@code hours} may be a decimal (1.5); {@code minutes} wins when both are given. */
    public record EntryRequest(Long projectId, Long taskId, LocalDate workDate, BigDecimal hours, Integer minutes, String description, Boolean billable) {
    }

    public record TimerStartRequest(Long projectId, Long taskId, String description, Boolean billable) {
    }

    public record TimerStopRequest(String description, Integer minutes) {
    }

    /** Hand in these entries, or every draft/rejected one between two dates when {@code ids} is empty. */
    public record SubmitRequest(List<Long> ids, LocalDate from, LocalDate to) {
    }

    public record DecisionRequest(List<Long> ids, String reason) {
    }

    public record SettingsRequest(Boolean requireApproval, Integer timerRoundingMinutes, BigDecimal defaultHourlyRate, Integer maxHoursPerDay,
                                  BigDecimal dailyCapacityHours, Long billingProductId) {
    }

    /** Which approved time to bill: these entries, or all billable approved time of the project up to {@code to}. */
    public record BillingRequest(Long projectId, Long customerId, LocalDate from, LocalDate to, List<Long> entryIds, LocalDate dueDate, String notes) {
    }
}
