package com.posapp.propertymgmt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Rent reminders and late fees. */
public final class PropertyMgmtReminderDtos {

    private PropertyMgmtReminderDtos() {}

    /** The settings; {@code mailReady} says whether e-mail is set up, {@code lastRun}/{@code lastSummary} what the morning job did last. */
    public record ReminderSettingsDto(boolean remindersEnabled, int reminderBeforeDays, int overdueFirstDays, int overdueRepeatDays, int overdueMaxReminders, boolean lateFeeEnabled,
                                      boolean lateFeeAuto, int lateFeeGraceDays, String lateFeeType, BigDecimal lateFeeValue, BigDecimal lateFeeMax, LocalDate lastRun, String lastSummary,
                                      boolean mailReady) {
    }

    public record ReminderSettingsRequest(Boolean remindersEnabled, Integer reminderBeforeDays, Integer overdueFirstDays, Integer overdueRepeatDays, Integer overdueMaxReminders,
                                          Boolean lateFeeEnabled, Boolean lateFeeAuto, Integer lateFeeGraceDays, String lateFeeType, BigDecimal lateFeeValue, BigDecimal lateFeeMax,
                                          Boolean clearLateFeeMax) {
    }

    /** What the job would do for one rent charge today: send a reminder ({@code reminder} is BEFORE or OVERDUE) and/or charge a late fee. {@code problem} says why a reminder cannot go. */
    public record PlanRow(Long chargeId, Long leaseId, String leaseCode, String tenantName, String tenantEmail, String unit, String invoiceNumber, LocalDate dueDate, BigDecimal outstanding,
                          Long daysOverdue, String reminder, BigDecimal lateFee, String problem) {
    }

    public record RunResult(int reminded, int fees, int skipped, int failed, List<String> notes) {
    }

    public record RemindRequest(String email, String message) {
    }

    public record ReminderSent(String sentTo) {
    }

    public record LateFeeDto(Long chargeId, BigDecimal amount, String invoiceNumber, LocalDate chargedOn) {
    }

    public record ReminderRow(Instant sentAt, String kind, String sentTo, String sentBy) {
    }
}
