package com.posapp.recurringexpense.dto;

import com.posapp.accounting.PayeeType;
import com.posapp.recurringexpense.RecurrenceFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RecurringExpenseTemplateRequest {

    @NotBlank(message = "Name is required")
    private String name;

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

    @NotNull(message = "Frequency is required")
    private RecurrenceFrequency frequency = RecurrenceFrequency.MONTHLY;

    @NotNull(message = "First billing date is required")
    private LocalDate nextRunDate;

    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
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
    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }
    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
