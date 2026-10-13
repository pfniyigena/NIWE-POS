package com.posapp.installment.dto;

import com.posapp.installment.InstallmentFrequency;
import com.posapp.installment.InstallmentPlanStatus;
import com.posapp.installment.InstallmentState;
import com.posapp.installment.InterestMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the instalment screens read. */
public final class InstallmentDtos {

    private InstallmentDtos() {
    }

    public record PlanSummaryDto(Long id, String planNumber, Long invoiceId, String invoiceNumber, Long customerId, String customerName,
                                 Long branchId, InstallmentPlanStatus status, BigDecimal financedAmount, BigDecimal totalInterest,
                                 BigDecimal totalPayable, BigDecimal paid, BigDecimal outstanding, BigDecimal overdueAmount,
                                 LocalDate nextDueDate, BigDecimal nextDueAmount, int numberOfInstallments, int paidInstallments,
                                 int overdueInstallments, Instant createdAt) {
    }

    public record InstallmentDto(int seq, LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue, BigDecimal lateFee,
                                 boolean lateFeeWaived, BigDecimal totalDue, BigDecimal paid, BigDecimal balance, InstallmentState state,
                                 long daysOverdue) {
    }

    public record PaymentDto(Long id, Instant paidAt, BigDecimal amount, BigDecimal principalPart, BigDecimal chargesPart, String method,
                             String receiptNumber, String note, String recordedByName) {
    }

    public record TermsDto(BigDecimal invoiceTotal, BigDecimal deposit, InterestMethod interestMethod, BigDecimal interestRate,
                           InstallmentFrequency frequency, LocalDate firstDueDate, int graceDays, BigDecimal lateFeeFixed,
                           BigDecimal lateFeePercent, boolean remindersEnabled, boolean wholeUnits, String notes, String adjustmentNote,
                           String createdByName, LocalDate cancelledOn, String cancelReason) {
    }

    public record PlanDetailDto(PlanSummaryDto plan, TermsDto terms, List<InstallmentDto> installments, List<PaymentDto> payments) {
    }

    /** One line of a schedule that hasn't been saved yet. */
    public record PreviewLineDto(int seq, LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue, BigDecimal totalDue) {
    }

    public record PreviewDto(BigDecimal invoiceTotal, BigDecimal alreadyPaid, BigDecimal outstandingBeforeDeposit, BigDecimal deposit,
                             BigDecimal financedAmount, BigDecimal totalInterest, BigDecimal totalPayable, List<PreviewLineDto> lines) {
    }

    public record SummaryDto(long activeAgreements, BigDecimal outstandingBalance, BigDecimal overdueAmount, long overdueInstallments,
                             BigDecimal dueThisMonth, BigDecimal collectedThisMonth) {
    }

    public record SettingsDto(boolean remindersEnabled, int reminderDaysBefore, int overdueReminderEveryDays, boolean defaultWholeUnits, BigDecimal defaultInterestRate,
                              int defaultGraceDays, BigDecimal defaultLateFeeFixed, BigDecimal defaultLateFeePercent) {
    }

    public record ReminderResultDto(boolean sent, String sentTo) {
    }

    /** One instalment of one plan, flattened for the reports. */
    public record InstallmentLine(Long planId, String planNumber, Long branchId, String customerName, String invoiceNumber, int seq, int of,
                                  LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue, BigDecimal lateFee, BigDecimal totalDue,
                                  BigDecimal paid, BigDecimal balance, InstallmentState state, long daysOverdue) {
    }
}
