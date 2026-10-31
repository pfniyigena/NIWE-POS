package com.posapp.accounting.dto;

import com.posapp.accounting.Expense;
import com.posapp.accounting.ExpenseStatus;
import com.posapp.accounting.PayeeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One expense. {@code amount} is the full total (including any recoverable VAT in {@code taxAmount}). */
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
        Long branchId,
        String expenseNumber,
        String reference,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        Long paidFromAccountId,
        String paidFromAccountName,
        LocalDate dueDate,
        BigDecimal amountPaid,
        BigDecimal balance,
        /** null for an expense that is not an open bill; otherwise OK, DUE_SOON or OVERDUE. */
        String dueState,
        Long createdByUserId,
        String createdByName,
        String approvedByName,
        String rejectedByName,
        String voidedByName,
        int attachmentCount,
        String budgetWarning
) {
    public static ExpenseDto from(Expense e) {
        return from(e, 7);
    }

    public static ExpenseDto from(Expense e, int dueSoonDays) {
        var account = e.getAccount();
        var costCenter = e.getCostCenter();
        var from = e.getPaidFromAccount();
        BigDecimal tax = e.getTaxAmount() != null ? e.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal paidSoFar = e.getAmountPaid() != null ? e.getAmountPaid() : BigDecimal.ZERO;
        boolean openBill = e.getStatus() == ExpenseStatus.APPROVED && !e.isPaid() && e.getSettledAt() == null;
        BigDecimal balance = !e.isPaid() && e.getStatus() == ExpenseStatus.APPROVED ? e.getAmount().subtract(paidSoFar).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        String dueState = null;
        if (openBill) {
            LocalDate today = LocalDate.now();
            if (e.getDueDate() == null) dueState = "OK";
            else if (e.getDueDate().isBefore(today)) dueState = "OVERDUE";
            else if (!e.getDueDate().isAfter(today.plusDays(dueSoonDays))) dueState = "DUE_SOON";
            else dueState = "OK";
        }
        return new ExpenseDto(e.getId(), e.getExpenseDate(),
                account != null ? account.getId() : null, account != null ? account.getCode() : null, account != null ? account.getName() : null,
                e.getPayeeType(), e.getPayeeId(), e.getPayeeName(), e.getAmount(), e.isPaid(), e.getMemo(),
                costCenter != null ? costCenter.getId() : null, costCenter != null ? costCenter.getName() : null,
                e.getStatus(), e.getApprovedAt(), e.getRejectedAt(), e.getRejectionReason(),
                e.getVoidedAt(), e.getVoidReason(), e.getSettledAt(), e.getReceiptDataUrl(), e.getRecurringTemplateId(), e.getBranchId(),
                e.getExpenseNumber(), e.getReference(), tax, e.getAmount().subtract(tax),
                from != null ? from.getId() : null, from != null ? from.getName() : null,
                e.getDueDate(), paidSoFar, balance, dueState,
                e.getCreatedByUserId(), e.getCreatedByName(), e.getApprovedByName(), e.getRejectedByName(), e.getVoidedByName(),
                0, null);
    }

    public ExpenseDto withExtras(int attachmentCount, String budgetWarning) {
        return new ExpenseDto(id, expenseDate, accountId, accountCode, accountName, payeeType, payeeId, payeeName, amount, paid, memo, costCenterId, costCenterName,
                status, approvedAt, rejectedAt, rejectionReason, voidedAt, voidReason, settledAt, receiptDataUrl, recurringTemplateId, branchId,
                expenseNumber, reference, taxAmount, netAmount, paidFromAccountId, paidFromAccountName, dueDate, amountPaid, balance, dueState,
                createdByUserId, createdByName, approvedByName, rejectedByName, voidedByName, attachmentCount, budgetWarning);
    }
}
