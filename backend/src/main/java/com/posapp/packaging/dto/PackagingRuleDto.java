package com.posapp.packaging.dto;


import java.math.BigDecimal;import com.posapp.packaging.PackagingRule;

public record PackagingRuleDto(
        Long id,
        Long parentProductId,
        String parentProductName,
        String parentProductSku,
        Long childProductId,
        String childProductName,
        String childProductSku,
        BigDecimal unitsPerParent,
        String notes,
        boolean active
) {
    public static PackagingRuleDto from(PackagingRule rule) {
        var parent = rule.getParentProduct();
        var child = rule.getChildProduct();
        return new PackagingRuleDto(
                rule.getId(),
                parent.getId(),
                parent.getName(),
                parent.getSku(),
                child.getId(),
                child.getName(),
                child.getSku(),
                rule.getUnitsPerParent(),
                rule.getNotes(),
                rule.isActive()
        );
    }
}
