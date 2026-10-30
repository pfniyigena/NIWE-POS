package com.posapp.installment.dto;

import com.posapp.installment.InstallmentFrequency;
import com.posapp.installment.InterestMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Bodies of the instalment endpoints. */
public final class InstallmentRequests {

    private InstallmentRequests() {
    }

    /** Used both to preview a schedule and to create the plan from it. */
    public record PlanRequest(Long invoiceId, BigDecimal deposit, String depositMethod, Integer numberOfInstallments,
                              InstallmentFrequency frequency, LocalDate firstDueDate, InterestMethod interestMethod, BigDecimal interestRate,
                              Integer graceDays, BigDecimal lateFeeFixed, BigDecimal lateFeePercent, Boolean remindersEnabled, Boolean wholeUnits, String notes) {
    }

    public record PaymentRequest(BigDecimal amount, String method, String note) {
    }

    public record CancelRequest(String reason) {
    }

    /** One instalment of the new schedule for what is still unpaid. */
    public record RescheduleLine(LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue) {
    }

    /** Replaces every instalment that isn't fully paid yet (see InstallmentService.reschedule). */
    public record RescheduleRequest(java.util.List<RescheduleLine> installments) {
    }

    public record SettingsRequest(Boolean remindersEnabled, Integer reminderDaysBefore, Integer overdueReminderEveryDays, Boolean defaultWholeUnits,
                                  BigDecimal defaultInterestRate, Integer defaultGraceDays, BigDecimal defaultLateFeeFixed,
                                  BigDecimal defaultLateFeePercent, BigDecimal payoffRebatePercent, Integer defaultAfterDays, String agreementTerms) {
    }

    /** A contact note; for kind PROMISE the date (and optionally the amount) promised is required/allowed. */
    public record FollowUpRequest(com.posapp.installment.InstallmentFollowUpKind kind, String note, LocalDate promisedOn, BigDecimal promisedAmount) {
    }

    /** Settles the plan in full today. {@code expectedAmount} is the figure the screen showed; the call is refused if it has changed since. */
    public record PayoffRequest(String method, String note, BigDecimal rebatePercent, BigDecimal expectedAmount) {
    }

    public record ReasonRequest(String reason) {
    }

    public record StatementEmailRequest(Boolean includeAgreement) {
    }
}
