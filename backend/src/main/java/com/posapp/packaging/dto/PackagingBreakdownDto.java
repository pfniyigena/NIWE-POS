package com.posapp.packaging.dto;

import com.posapp.packaging.PackagingBreakdown;

import java.math.BigDecimal;
import java.time.Instant;

public record PackagingBreakdownDto(
        Long id,
        Long ruleId,
        Long parentProductId,
        String parentProductName,
        Long childProductId,
        String childProductName,
        int unitsPerParent,
        int parentQuantity,
        int childQuantityProduced,
        Integer parentStockBefore,
        Integer parentStockAfter,
        Integer childStockBefore,
        Integer childStockAfter,
        /** Fourier: "the cost of the child should be equal to the cost of the parent divided by unit" — see PackagingBreakdown.childUnitCost's own javadoc. */
        BigDecimal childUnitCost,
        String note,
        Instant createdAt,
        /** Multi-warehouse phase 4 — null for a breakdown on the primary warehouse's normal stock (all older rows). */
        Long locationId,
        String locationName,
        String warehouseName
) {
    public static PackagingBreakdownDto from(PackagingBreakdown b) {
        return new PackagingBreakdownDto(
                b.getId(),
                b.getRule() != null ? b.getRule().getId() : null,
                b.getParentProduct() != null ? b.getParentProduct().getId() : null,
                b.getParentProductName(),
                b.getChildProduct() != null ? b.getChildProduct().getId() : null,
                b.getChildProductName(),
                b.getUnitsPerParent(),
                b.getParentQuantity(),
                b.getChildQuantityProduced(),
                b.getParentStockBefore(),
                b.getParentStockAfter(),
                b.getChildStockBefore(),
                b.getChildStockAfter(),
                b.getChildUnitCost(),
                b.getNote(),
                b.getCreatedAt(),
                b.getLocation() != null ? b.getLocation().getId() : null,
                b.getLocationName(),
                b.getWarehouseName()
        );
    }
}
