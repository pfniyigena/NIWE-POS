package com.posapp.warehouse.dto;

import com.posapp.warehouse.StockTransfer;

import java.time.Instant;

public record StockTransferDto(
        Long id,
        Long productId,
        String productName,
        Long fromLocationId,
        String fromLocationName,
        String fromWarehouseName,
        Long toLocationId,
        String toLocationName,
        String toWarehouseName,
        int quantity,
        String note,
        /** "PENDING" | "CONFIRMED" | "REJECTED" — see StockTransferStatus's own javadoc. */
        String status,
        Instant createdAt,
        /** Null until an admin (or a PERM_APPROVE_STOCK_TRANSFER holder) confirms/rejects it. */
        Instant confirmedAt,
        /** Branch of each end's warehouse (snapshot); null on transfers made before branches existed. */
        Long fromBranchId,
        String fromBranchName,
        Long toBranchId,
        String toBranchName,
        /** True when the two ends belong to different branches. */
        boolean interBranch,
        /** Inter-branch only: when it was dispatched / received and how many units arrived. */
        Instant dispatchedAt,
        Instant receivedAt,
        Integer receivedQuantity,
        String receiveNote
) {
    public static StockTransferDto from(StockTransfer t) {
        return new StockTransferDto(
                t.getId(),
                t.getProduct() != null ? t.getProduct().getId() : null,
                t.getProductName(),
                t.getFromLocation() != null ? t.getFromLocation().getId() : null,
                t.getFromLocationName(),
                t.getFromWarehouseName(),
                t.getToLocation() != null ? t.getToLocation().getId() : null,
                t.getToLocationName(),
                t.getToWarehouseName(),
                t.getQuantity(),
                t.getNote(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getCreatedAt(),
                t.getConfirmedAt(),
                t.getFromBranchId(),
                t.getFromBranchName(),
                t.getToBranchId(),
                t.getToBranchName(),
                t.getFromBranchId() != null && t.getToBranchId() != null && !t.getFromBranchId().equals(t.getToBranchId()),
                t.getDispatchedAt(),
                t.getReceivedAt(),
                t.getReceivedQuantity(),
                t.getReceiveNote()
        );
    }
}
