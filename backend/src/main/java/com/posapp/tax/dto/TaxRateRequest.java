package com.posapp.tax.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Create request — code is set once and never editable afterward (see UpdateTaxRateRequest). */
public class TaxRateRequest {

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0", message = "Rate cannot be negative")
    @DecimalMax(value = "100", message = "Rate cannot exceed 100%")
    private BigDecimal ratePercent;

    /** See TaxRate.inclusive's javadoc. Defaults to false (tax added on top) if omitted. */
    private boolean inclusive = false;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    public boolean isInclusive() { return inclusive; }
    public void setInclusive(boolean inclusive) { this.inclusive = inclusive; }
}
