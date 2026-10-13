package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Row types of the Purchases reports (Purchases > Reports). One file for the whole family, since they are small and
 * only used together by {@code PurchaseReportService} and the matching {@code ReportController} endpoints.
 */
public final class PurchaseReportRows {

    private PurchaseReportRows() {
    }

    /** One supplier's confirmed purchase orders (sent / partly or fully received) in the period. */
    public record SupplierPurchaseRow(Long supplierId, String supplierName, int orderCount, int quantityOrdered,
                                      int quantityReceived, BigDecimal netCost, BigDecimal taxCost, BigDecimal totalCost,
                                      BigDecimal amountPaid, BigDecimal amountOutstanding) {
    }

    /** One product bought in the period. {@code averageUnitCost} is the net cost per unit ordered. */
    public record ProductPurchaseRow(Long productId, String sku, String productName, String categoryName, int quantityOrdered,
                                     int quantityReceived, BigDecimal netCost, BigDecimal taxCost, BigDecimal totalCost,
                                     BigDecimal averageUnitCost) {
    }

    public record CategoryPurchaseRow(String categoryName, int quantityOrdered, int quantityReceived, BigDecimal netCost,
                                      BigDecimal taxCost, BigDecimal totalCost) {
    }

    public record BillReportRow(Long billId, String billNumber, String purchaseOrderNumber, String supplierName, String status,
                                Instant billDate, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal amountOutstanding) {
    }

    public record PurchaseReturnReportRow(Long returnId, String returnNumber, String purchaseOrderNumber, String supplierName,
                                          Instant returnDate, String reason, int quantity, BigDecimal refundTotal) {
    }

    public record SupplierPaymentReportRow(Long paymentId, String receiptNumber, String supplierName, Instant paidAt,
                                           String method, BigDecimal amount, String note) {
    }

    /** What is owed to one supplier right now, by how long ago the order (or its bill) is. */
    public record PayablesAgingRow(Long supplierId, String supplierName, BigDecimal days0To30, BigDecimal days31To60,
                                   BigDecimal days61To90, BigDecimal over90Days, BigDecimal totalOutstanding) {
    }
}
