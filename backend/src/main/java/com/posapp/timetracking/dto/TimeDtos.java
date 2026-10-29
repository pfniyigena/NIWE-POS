package com.posapp.timetracking.dto;

import com.posapp.timetracking.ProjectStatus;
import com.posapp.timetracking.TimeEntryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the time endpoints return. Hours are decimals (minutes / 60) so screens can show "7.5". */
public final class TimeDtos {

    private TimeDtos() {
    }

    public record StaffOption(Long id, String fullName) {
    }

    public record SettingsDto(boolean requireApproval, int timerRoundingMinutes, BigDecimal defaultHourlyRate, int maxHoursPerDay,
                              BigDecimal dailyCapacityHours, Long billingProductId, String billingProductName, int timerAutoStopHours, LocalDate lockedThrough,
                              boolean reminderEnabled, int reminderDay, int budgetWarnPercent) {
    }

    public record TaskDto(Long id, Long projectId, String name, BigDecimal estimatedHours, boolean active, BigDecimal hoursLogged) {
    }

    public record RateDto(Long userId, String userName, BigDecimal hourlyRate) {
    }

    /** A project for the lists. */
    public record ProjectSummaryDto(Long id, String name, String code, Long customerId, String customerName, ProjectStatus status, boolean billable,
                                    BigDecimal defaultRate, BigDecimal budgetHours, BigDecimal hoursLogged, BigDecimal billableHours,
                                    BigDecimal unbilledAmount, BigDecimal percentOfBudget, String sourceType, Long sourceId,
                                    LocalDate startDate, LocalDate endDate, boolean restrictToTeam, boolean blockOverBudget) {
    }

    public record MemberDto(Long userId, String userName) {
    }

    public record ProjectDetailDto(ProjectSummaryDto summary, String description, Long branchId, List<TaskDto> tasks, List<RateDto> rates, List<MemberDto> team) {
    }

    /** A project as offered in the "log time" pickers. */
    public record ProjectChoice(Long id, String name, String code, String customerName, boolean billable, List<TaskDto> tasks, BigDecimal budgetHours,
                                BigDecimal hoursLogged, boolean blockOverBudget) {
    }

    public record EntryDto(Long id, Long userId, String userName, Long projectId, String projectName, String customerName, Long taskId, String taskName,
                           LocalDate workDate, int minutes, BigDecimal hours, String description, boolean billable, BigDecimal hourlyRate,
                           BigDecimal amount, TimeEntryStatus status, String origin, Instant submittedAt, String decidedByName, Instant decidedAt,
                           String rejectReason, Long invoiceId, String invoiceNumber) {
    }

    public record TimerDto(boolean running, Long projectId, String projectName, Long taskId, String taskName, String description, boolean billable,
                           Instant startedAt, long elapsedSeconds, int autoStopHours) {
    }

    /** Totals for one person's week. */
    public record WeekDto(LocalDate from, LocalDate to, List<EntryDto> entries, BigDecimal totalHours, BigDecimal billableHours, int draftCount,
                          int submittedCount, int approvedCount, int rejectedCount, int invoicedCount) {
    }

    /** The cards on a person's page. */
    public record MySummaryDto(BigDecimal hoursToday, BigDecimal hoursThisWeek, BigDecimal billableHoursThisWeek, BigDecimal hoursThisMonth,
                               int toSubmit, int rejected) {
    }

    /** The cards for managers. */
    public record SummaryDto(int pendingApprovals, BigDecimal hoursThisMonth, BigDecimal billableHoursThisMonth, BigDecimal unbilledAmount,
                             int activeProjects, int overBudgetProjects) {
    }

    public record InvoiceLinePreview(Long userId, String userName, BigDecimal hourlyRate, BigDecimal hours, BigDecimal amount, String description,
                                     int entries) {
    }

    public record BillingPreviewDto(Long projectId, String projectName, Long customerId, String customerName, List<Long> entryIds,
                                    List<InvoiceLinePreview> lines, BigDecimal totalHours, BigDecimal totalAmount) {
    }

    public record BillingResultDto(Long invoiceId, String invoiceNumber, int entries, BigDecimal totalAmount) {
    }

    /** One person whose timesheet for a week is not complete. status: NOTHING (no time logged) or DRAFTS (some time not submitted) or SENT_BACK. */
    public record MissingRow(Long userId, String userName, boolean hasEmail, String status, BigDecimal hoursLogged, BigDecimal hoursNotSubmitted,
                             BigDecimal hoursSubmitted) {
    }

    public record MissingDto(LocalDate weekStart, LocalDate weekEnd, List<MissingRow> rows) {
    }

    public record ReminderResult(int sent, int noEmail, int failed, List<String> notes) {
    }

    public record CopyResult(int copied, int skipped, List<String> notes) {
    }
}
