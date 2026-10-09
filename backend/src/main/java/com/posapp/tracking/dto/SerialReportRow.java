package com.posapp.tracking.dto;

/** One serial number for the serial register report. */
public record SerialReportRow(
        Long productId,
        String sku,
        String productName,
        String serialNumber,
        String status,
        String warehouseName,
        String locationName,
        String receivedOn,
        String source,
        String leftOn,
        String leftRef
) {
}
