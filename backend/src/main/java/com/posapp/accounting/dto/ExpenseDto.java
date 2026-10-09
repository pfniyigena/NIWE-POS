package com.posapp.accounting.dto;

import com.posapp.accounting.Expense;
import com.posapp.accounting.ExpenseStatus;
import com.posapp.accounting.PayeeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseDto(
        Long id,
        LocalDate expenseDate,
        Long accountId,
        String accountCode,
        String accountName,
        PayeeType payeeType,
        Long payeeId,
        String payeeName,
        BigDecimal amount,
        boolean paid,
        String memo,
        Long costCenterId,
        String costCenterName,
        ExpenseStatus status,
        Instant approvedAt,
        Instant rejectedAt,
        String rejectionReason,
        Instant voidedAt,
        String voidReason,
        Instant settledAt,
        String receiptDataUrl,
        Long recurringTemplateId,
        /** The branch this expense belongs to; null on legacy rows (= head office). */
        Long branchId
) {
    public static ExpenseDto from(Expense e) {
        var account = e.getAccount();
        var costCenter = e.getCostCenter();
        return new ExpenseDto(e.getId(), e.getExpenseDate(),
                account != null ? account.getId() : null, account != null ? account.getCode() : null, account != null ? account.getName() : null,
                e.getPayeeType(), e.getPayeeId(), e.getPayeeName(), e.getAmount(), e.isPaid(), e.getMemo(),
                costCenter != null ? costCenter.getId() : null, costCenter != null ? costCenter.getName() : null,
                e.getStatus(), e.getApprovedAt(), e.getRejectedAt(), e.getRejectionReason(),
                e.getVoidedAt(), e.getVoidReason(), e.getSettledAt(), e.getReceiptDataUrl(), e.getRecurringTemplateId(), e.getBranchId());
    }
}
