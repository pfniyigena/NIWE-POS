package com.posapp.tax.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Update request — deliberately excludes code: it's the stable identifier stored on historical OrderLine rows. */
public class UpdateTaxRateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0", message = "Rate cannot be negative")
    @DecimalMax(value = "100", message = "Rate cannot exceed 100%")
    private BigDecimal ratePercent;

    private boolean active = true;

    /** See TaxRate.inclusive's javadoc. */
    private boolean inclusive = false;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isInclusive() { return inclusive; }
    public void setInclusive(boolean inclusive) { this.inclusive = inclusive; }
}
