package com.posapp.accounting.dto;

import jakarta.validation.constraints.NotBlank;

public class CostCenterRequest {

    /** Only read on create — a cost center's code is stable for life, same convention as Account.code. */
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
