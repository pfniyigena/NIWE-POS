package com.posapp.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class CustomerRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String phone;
    private String email;
    private String taxId;

    /** Task: "Enable credit for customer". */
    private boolean creditEnabled;

    /** Task: "Set customer credit limit". Defaults to zero (see Customer.creditLimit's javadoc) if omitted. */
    @PositiveOrZero(message = "Credit limit cannot be negative")
    private BigDecimal creditLimit;

    /** Task: "Optional due date/payment terms" — a customer-level default, nullable, optionally overridden per sale at checkout. */
    private Integer defaultCreditTermDays;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public boolean isCreditEnabled() { return creditEnabled; }
    public void setCreditEnabled(boolean creditEnabled) { this.creditEnabled = creditEnabled; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public Integer getDefaultCreditTermDays() { return defaultCreditTermDays; }
    public void setDefaultCreditTermDays(Integer defaultCreditTermDays) { this.defaultCreditTermDays = defaultCreditTermDays; }
}
