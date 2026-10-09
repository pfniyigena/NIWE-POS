package com.posapp.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Admin > Settings > System settings (Task: "Add System setting like currency, time zone, date format, etc"). */
public class SystemSettingsRequest {

    @NotBlank(message = "Currency code is required")
    private String currencyCode;

    @NotBlank(message = "Currency symbol is required")
    private String currencySymbol;

    @NotBlank(message = "Time zone is required")
    private String timeZone;

    @NotBlank(message = "Date format is required")
    private String dateFormat;

    /** Task 1: "configure the max discount" — see StoreSettings.maxDiscountPercent's javadoc. */
    @NotNull(message = "Max discount is required")
    @DecimalMin(value = "0", message = "Max discount must be between 0 and 100")
    @DecimalMax(value = "100", message = "Max discount must be between 0 and 100")
    private BigDecimal maxDiscountPercent;

    /**
     * Fourier: "discount should be activate in the setting or configuration ... Either on line
     * or global" — one of "NONE"/"PER_LINE"/"GLOBAL" (see StoreSettings.discountMode's javadoc);
     * validated and parsed in StoreSettingsService.updateSystemSettings rather than with a
     * jakarta.validation annotation here, so an invalid value gets the same ApiException.badRequest
     * treatment as every other business-rule rejection in this app instead of a generic 400.
     */
    @NotBlank(message = "Discount mode is required")
    private String discountMode;

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public BigDecimal getMaxDiscountPercent() { return maxDiscountPercent; }
    public void setMaxDiscountPercent(BigDecimal maxDiscountPercent) { this.maxDiscountPercent = maxDiscountPercent; }
    public String getDiscountMode() { return discountMode; }
    public void setDiscountMode(String discountMode) { this.discountMode = discountMode; }
}
