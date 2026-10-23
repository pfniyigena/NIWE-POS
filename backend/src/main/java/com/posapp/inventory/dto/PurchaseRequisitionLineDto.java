package com.posapp.inventory.dto;


import java.math.BigDecimal;import com.posapp.inventory.PurchaseRequisitionLine;

public record PurchaseRequisitionLineDto(
        Long id,
        Long productId,
        String productName,
        BigDecimal quantityRequested,
        String note
) {
    public static PurchaseRequisitionLineDto from(PurchaseRequisitionLine l) {
        return new PurchaseRequisitionLineDto(
                l.getId(),
                l.getProduct() != null ? l.getProduct().getId() : null,
                l.getProductName(),
                l.getQuantityRequested(),
                l.getNote()
        );
    }
}
