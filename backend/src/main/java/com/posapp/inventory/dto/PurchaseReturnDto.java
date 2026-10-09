package com.posapp.inventory.dto;

import com.posapp.inventory.PurchaseReturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseReturnDto(
        Long id,
        Long purchaseOrderId,
        String purchaseOrderNumber,
        Long supplierId,
        String supplierName,
        String returnNumber,
        Instant returnDate,
        String reason,
        BigDecimal refundTotal,
        List<PurchaseReturnLineDto> lines
) {
    public static PurchaseReturnDto from(PurchaseReturn r) {
        var po = r.getPurchaseOrder();
        var supplier = po != null ? po.getSupplier() : null;
        return new PurchaseReturnDto(
                r.getId(),
                po != null ? po.getId() : null,
                po != null ? po.getOrderNumber() : null,
                supplier != null ? supplier.getId() : null,
                supplier != null ? supplier.getName() : null,
                r.getReturnNumber(),
                r.getReturnDate(),
                r.getReason(),
                r.getRefundTotal(),
                r.getLines().stream().map(PurchaseReturnLineDto::from).toList()
        );
    }
}
