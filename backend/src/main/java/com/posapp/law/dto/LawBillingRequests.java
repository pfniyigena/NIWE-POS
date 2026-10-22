package com.posapp.law.dto;

import com.posapp.law.BillingType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LawBillingRequests {

    private LawBillingRequests() {
    }

    public record FeeTermsRequest(BillingType billingType, String billingNote, BigDecimal hourlyRate, BigDecimal fixedFee, BigDecimal retainerAmount,
                                  BigDecimal contingencyPercent) {
    }

    public record StageRequest(String description, BigDecimal amount, LocalDate dueDate) {
    }

    /** {@code expenseAccountId}: also record the money spent in Expenses (needs the Expenses module). */
    public record CostRequest(LocalDate expenseDate, String description, String supplierName, BigDecimal amount, BigDecimal chargeAmount, Long expenseAccountId) {
    }

    /** What to put on the next invoice: approved time up to a date, chosen stages and costs, a contingency fee, and whether a retainer is used up. */
    public record BillRequest(Boolean includeTime, LocalDate timeTo, List<Long> stageIds, List<Long> costIds, BigDecimal recoveredAmount, Boolean applyRetainer,
                              LocalDate dueDate, String notes) {
    }

    public record RetainerRequest(BigDecimal amount, LocalDate dueDate, String notes) {
    }

    public record RateRequest(Long userId, BigDecimal hourlyRate) {
    }

    public record DecisionRequest(List<Long> ids, String reason) {
    }
}
