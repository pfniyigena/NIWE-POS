package com.posapp.accounting.dto;

import com.posapp.accounting.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AccountRequest {

    /** Only read on create — an account's code is stable for life, same convention as Workstation.code/PaymentMethodEntity.code. */
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Account type is required")
    private AccountType type;

    private String description;

    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
