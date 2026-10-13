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
                                  BigDecimal defaultLateFeePercent) {
    }
}
