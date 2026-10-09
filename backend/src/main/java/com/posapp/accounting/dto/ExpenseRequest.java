package com.posapp.accounting.dto;

import com.posapp.accounting.PayeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseRequest {

    @NotNull(message = "Date is required")
    private LocalDate expenseDate;

    @NotNull(message = "Category (account) is required")
    private Long accountId;

    @NotNull(message = "Payee type is required")
    private PayeeType payeeType = PayeeType.OTHER;

    private Long payeeId;

    @NotBlank(message = "Payee name is required")
    private String payeeName;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private boolean paid = true;

    private String memo;

    private Long costCenterId;

    /** Nullable data: URL for an uploaded photo/PDF of the receipt/bill — see Expense.receiptDataUrl's javadoc. */
    private String receiptDataUrl;

    /** Optional branch this expense belongs to; null = head office (or unchanged when editing). */
    private Long branchId;

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public PayeeType getPayeeType() { return payeeType; }
    public void setPayeeType(PayeeType payeeType) { this.payeeType = payeeType; }
    public Long getPayeeId() { return payeeId; }
    public void setPayeeId(Long payeeId) { this.payeeId = payeeId; }
    public String getPayeeName() { return payeeName; }
    public void setPayeeName(String payeeName) { this.payeeName = payeeName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public Long getCostCenterId() { return costCenterId; }
    public void setCostCenterId(Long costCenterId) { this.costCenterId = costCenterId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getReceiptDataUrl() { return receiptDataUrl; }
    public void setReceiptDataUrl(String receiptDataUrl) { this.receiptDataUrl = receiptDataUrl; }
}
