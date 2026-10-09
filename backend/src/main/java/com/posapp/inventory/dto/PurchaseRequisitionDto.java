package com.posapp.inventory.dto;

import com.posapp.inventory.PurchaseRequisition;

import java.time.Instant;
import java.util.List;

public record PurchaseRequisitionDto(
        Long id,
        String requisitionNumber,
        String status,
        Instant requestDate,
        String requestedByFullName,
        String notes,
        List<PurchaseRequisitionLineDto> lines,
        Instant approvedAt,
        String approvedByFullName,
        Instant rejectedAt,
        String rejectionReason,
        Long convertedPurchaseOrderId,
        String convertedPurchaseOrderNumber
) {
    public static PurchaseRequisitionDto from(PurchaseRequisition r) {
        return new PurchaseRequisitionDto(
                r.getId(),
                r.getRequisitionNumber(),
                r.getStatus().name(),
                r.getRequestDate(),
                r.getRequestedByFullName(),
                r.getNotes(),
                r.getLines().stream().map(PurchaseRequisitionLineDto::from).toList(),
                r.getApprovedAt(),
                r.getApprovedByFullName(),
                r.getRejectedAt(),
                r.getRejectionReason(),
                r.getConvertedPurchaseOrderId(),
                r.getConvertedPurchaseOrderNumber()
        );
    }
}
