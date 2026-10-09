package com.posapp.salesorder.dto;

import com.posapp.salesorder.SalesOrderReturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SalesOrderReturnDto(
        Long id,
        Long salesOrderId,
        String salesOrderNumber,
        Long customerId,
        String customerName,
        String returnNumber,
        Instant returnDate,
        String reason,
        BigDecimal refundTotal,
        List<SalesOrderReturnLineDto> lines,
        /** Multi-warehouse phase 3 — where the goods went back; null for older returns (primary warehouse stock). */
        Long locationId,
        String locationName,
        String warehouseName
) {
    public static SalesOrderReturnDto from(SalesOrderReturn r) {
        var so = r.getSalesOrder();
        var customer = so != null ? so.getCustomer() : null;
        return new SalesOrderReturnDto(
                r.getId(),
                so != null ? so.getId() : null,
                so != null ? so.getOrderNumber() : null,
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : null,
                r.getReturnNumber(),
                r.getReturnDate(),
                r.getReason(),
                r.getRefundTotal(),
                r.getLines().stream().map(SalesOrderReturnLineDto::from).toList(),
                r.getLocation() != null ? r.getLocation().getId() : null,
                r.getLocationName(),
                r.getWarehouseName()
        );
    }
}
