package com.posapp.accounting.dto;

import com.posapp.accounting.PayeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public class IncomeRequest {

    @NotNull(message = "Date is required")
    private LocalDate incomeDate;

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

    private boolean received = true;

    private String memo;

    private Long costCenterId;

    public LocalDate getIncomeDate() { return incomeDate; }
    public void setIncomeDate(LocalDate incomeDate) { this.incomeDate = incomeDate; }
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
    public boolean isReceived() { return received; }
    public void setReceived(boolean received) { this.received = received; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    /** Optional branch; null = head office (or unchanged when editing a draft). */
    private Long branchId;
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }

    public Long getCostCenterId() { return costCenterId; }
    public void setCostCenterId(Long costCenterId) { this.costCenterId = costCenterId; }
}
