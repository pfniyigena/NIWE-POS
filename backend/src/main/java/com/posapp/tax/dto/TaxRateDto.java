package com.posapp.tax.dto;

import com.posapp.tax.TaxRate;

import java.math.BigDecimal;

public record TaxRateDto(Long id, String code, String name, BigDecimal ratePercent, boolean active, boolean inclusive) {
    public static TaxRateDto from(TaxRate taxRate) {
        return new TaxRateDto(taxRate.getId(), taxRate.getCode(), taxRate.getName(), taxRate.getRatePercent(), taxRate.isActive(), taxRate.isInclusive());
    }
}
