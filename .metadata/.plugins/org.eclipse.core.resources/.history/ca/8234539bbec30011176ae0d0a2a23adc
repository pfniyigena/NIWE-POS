package com.posapp.catalog.dto;

import com.posapp.catalog.Product;

import java.math.BigDecimal;

public record ProductDto(
        Long id,
        String sku,
        String barcode,
        String code,
        String iconDataUrl,
        String name,
        /** See Product.description's javadoc — the default text an Invoice Line's own description snapshots in. */
        String description,
        BigDecimal unitPrice,
        BigDecimal costPrice,
        int stockQuantity,
        /** See Product.trackStock's javadoc. */
        boolean trackStock,
        Long categoryId,
        String categoryName,
        String categoryColor,
        Long brandId,
        String brandName,
        Long taxRateId,
        String taxRateCode,
        String taxRateName,
        BigDecimal taxRatePercent,
        boolean taxRateInclusive,
        boolean active,
        String unspCode,
        boolean service,
        Integer reorderLevel,
        Integer maxStockLevel,
        /** Variants: the template this product was generated from, its option values, or (on a template) the option summary. */
        Long variantParentId,
        String variantLabel,
        String variantAttributes,
        /** NONE, BATCH or SERIAL — see Product.trackingMode. */
        String trackingMode
) {
    public static ProductDto from(Product p) {
        var category = p.getCategory();
        var brand = p.getBrand();
        var taxRate = p.getTaxRate();
        return new ProductDto(
                p.getId(),
                p.getSku(),
                p.getBarcode(),
                p.getCode(),
                p.getIconDataUrl(),
                p.getName(),
                p.getDescription(),
                p.getUnitPrice(),
                p.getCostPrice(),
                p.getStockQuantity(),
                p.isTrackStock(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                category != null ? category.getColorHex() : null,
                brand != null ? brand.getId() : null,
                brand != null ? brand.getName() : null,
                taxRate != null ? taxRate.getId() : null,
                taxRate != null ? taxRate.getCode() : null,
                taxRate != null ? taxRate.getName() : null,
                taxRate != null ? taxRate.getRatePercent() : null,
                taxRate != null && taxRate.isInclusive(),
                p.isActive(),
                p.getUnspCode(),
                p.isService(),
                p.getReorderLevel(),
                p.getMaxStockLevel(),
                p.getVariantParentId(),
                p.getVariantLabel(),
                p.getVariantAttributes(),
                p.getTrackingMode() != null ? p.getTrackingMode().name() : "NONE"
        );
    }
}
