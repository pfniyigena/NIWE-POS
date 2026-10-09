package com.posapp.inventory.dto;

import com.posapp.inventory.SupplierPrice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierPriceDto(
        Long id,
        Long supplierId,
        String supplierName,
        Long productId,
        String productName,
        String sku,
        BigDecimal unitCost,
        LocalDate validFrom,
        LocalDate validTo,
        String note
) {
    public static SupplierPriceDto from(SupplierPrice p) {
        return new SupplierPriceDto(
                p.getId(),
                p.getSupplier() != null ? p.getSupplier().getId() : null,
                p.getSupplier() != null ? p.getSupplier().getName() : null,
                p.getProduct() != null ? p.getProduct().getId() : null,
                p.getProduct() != null ? p.getProduct().getName() : null,
                p.getProduct() != null ? p.getProduct().getSku() : null,
                p.getUnitCost(),
                p.getValidFrom(),
                p.getValidTo(),
                p.getNote());
    }
}
