package com.posapp.inventory.dto;

import com.posapp.inventory.PurchaseRequisitionLine;

public record PurchaseRequisitionLineDto(
        Long id,
        Long productId,
        String productName,
        int quantityRequested,
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
