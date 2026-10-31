package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Small request and response types for the Expenses screens beyond the expense itself. */
public final class ExpenseExtras {
    private ExpenseExtras() {
    }

    public record PaymentDto(Long id, BigDecimal amount, LocalDate paidOn, Long paidFromAccountId, String paidFromAccountName, String note, String createdByName, Instant createdAt) {
    }

    public record AttachmentDto(Long id, String fileName, String contentType, long sizeBytes, String uploadedBy, Instant createdAt, boolean image) {
    }

    public record EventDto(Long id, String kind, String detail, String by, Instant at) {
    }

    /** An account money can be paid from (Cash, a bank, mobile money…). */
    public record PaymentAccountDto(Long id, String code, String name, boolean cash) {
    }

    public static class PayRequest {
        private BigDecimal amount;
        private LocalDate paidOn;
        private Long paidFromAccountId;
        private String note;

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDate getPaidOn() { return paidOn; }
        public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
        public Long getPaidFromAccountId() { return paidFromAccountId; }
        public void setPaidFromAccountId(Long paidFromAccountId) { this.paidFromAccountId = paidFromAccountId; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class BulkRequest {
        private List<Long> ids;
        /** APPROVE or REJECT. */
        private String action;
        private String reason;

        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public record Skipped(Long id, String expenseNumber, String reason) {
    }

    public record BulkResult(int done, List<Skipped> skipped) {
    }

    public static class SettingsRequest {
        private BigDecimal approvalLimit;
        private Boolean blockSelfApproval;
        private Boolean blockOverBudget;
        private Integer defaultDueDays;
        private Integer dueSoonDays;

        public BigDecimal getApprovalLimit() { return approvalLimit; }
        public void setApprovalLimit(BigDecimal approvalLimit) { this.approvalLimit = approvalLimit; }
        public Boolean getBlockSelfApproval() { return blockSelfApproval; }
        public void setBlockSelfApproval(Boolean blockSelfApproval) { this.blockSelfApproval = blockSelfApproval; }
        public Boolean getBlockOverBudget() { return blockOverBudget; }
        public void setBlockOverBudget(Boolean blockOverBudget) { this.blockOverBudget = blockOverBudget; }
        public Integer getDefaultDueDays() { return defaultDueDays; }
        public void setDefaultDueDays(Integer defaultDueDays) { this.defaultDueDays = defaultDueDays; }
        public Integer getDueSoonDays() { return dueSoonDays; }
        public void setDueSoonDays(Integer dueSoonDays) { this.dueSoonDays = dueSoonDays; }
    }

    public record SettingsDto(BigDecimal approvalLimit, boolean blockSelfApproval, boolean blockOverBudget, int defaultDueDays, int dueSoonDays) {
    }

    public static class BudgetRequest {
        private Long accountId;
        private Long costCenterId;
        private Integer year;
        private BigDecimal monthlyAmount;
        private String note;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public Long getCostCenterId() { return costCenterId; }
        public void setCostCenterId(Long costCenterId) { this.costCenterId = costCenterId; }
        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public BigDecimal getMonthlyAmount() { return monthlyAmount; }
        public void setMonthlyAmount(BigDecimal monthlyAmount) { this.monthlyAmount = monthlyAmount; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public record BudgetDto(Long id, Long accountId, String accountCode, String accountName, Long costCenterId, String costCenterName, int year, BigDecimal monthlyAmount, String note) {
    }

    /** Budget against what was spent in one month. spent = approved expenses net of recoverable VAT; pending = awaiting approval. */
    public record BudgetStatusRow(Long budgetId, String label, String kind, BigDecimal budget, BigDecimal spent, BigDecimal pending, BigDecimal remaining, Double percentUsed, String state) {
    }

    public record NamedAmount(String name, BigDecimal amount, int count) {
    }

    public record MonthAmount(String month, BigDecimal amount) {
    }

    public record Attention(Long id, String expenseNumber, String payee, BigDecimal amount, BigDecimal balance, LocalDate dueDate, String reason) {
    }

    public record SummaryDto(
            BigDecimal thisMonth, BigDecimal lastMonth, BigDecimal yearToDate,
            int awaitingApprovalCount, BigDecimal awaitingApprovalAmount,
            int unpaidCount, BigDecimal unpaidBalance,
            int overdueCount, BigDecimal overdueBalance,
            int dueSoonCount, BigDecimal dueSoonBalance,
            BigDecimal recoverableVatThisMonth,
            List<NamedAmount> topCategories, List<NamedAmount> topVendors, List<NamedAmount> byPaymentAccount,
            List<MonthAmount> lastSixMonths,
            List<BudgetStatusRow> budgetAlerts,
            List<Attention> attention) {
    }
}
