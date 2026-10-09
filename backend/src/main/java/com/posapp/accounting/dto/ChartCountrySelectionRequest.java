package com.posapp.accounting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/accounting/chart-setup}'s request body — the setup wizard's own submission.
 * {@code country} is validated loosely here (just non-blank) and strictly parsed with {@code
 * ChartCountry.fromConfigValue} in {@code ChartSetupController}, so a bad value gets that enum's
 * own fail-fast message (listing the valid options) rather than a generic Jakarta validation one.
 */
public class ChartCountrySelectionRequest {

    @NotBlank(message = "Country is required")
    private String country;

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
