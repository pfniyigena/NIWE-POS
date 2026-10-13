package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Row types of the Sales reports built on sale orders, invoices, credit notes, returns and customer payments (Sales >
 * Reports) — the selling mirror of {@link PurchaseReportRows}. Not the POS reports, which have their own rows.
 */
public final class SalesReportRows {

    private SalesReportRows() {
    }

    public record CustomerSalesRow(Long customerId, String customerName, int orderCount, int quantityOrdered, int quantityFulfilled,
                                   BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal amountPaid,
                                   BigDecimal amountOutstanding) {
    }

    public record ProductSalesOrderRow(Long productId, String sku, String productName, String categoryName, int quantityOrdered,
                                       int quantityFulfilled, BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                                       BigDecimal averageUnitPrice) {
    }

    public record CategorySalesOrderRow(String categoryName, int quantityOrdered, int quantityFulfilled, BigDecimal netAmount,
                                        BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    public record InvoiceReportRow(Long invoiceId, String invoiceNumber, String salesOrderNumber, String customerName, String status,
                                   Instant invoiceDate, LocalDate dueDate, BigDecimal totalAmount, BigDecimal amountPaid,
                                   BigDecimal amountOutstanding) {
    }

    /** A voided invoice and the credit note that cancelled it. */
    public record CreditNoteReportRow(Long invoiceId, String creditNoteNumber, String invoiceNumber, String customerName,
                                      Instant voidedAt, BigDecimal totalAmount, String reason, String paymentDisposition) {
    }

    public record SalesReturnReportRow(Long returnId, String returnNumber, String salesOrderNumber, String customerName,
                                       Instant returnDate, String reason, int quantity, BigDecimal refundTotal) {
    }

    public record CustomerPaymentReportRow(Long paymentId, String receiptNumber, String customerName, Instant paidAt, String method,
                                           BigDecimal amount, String note) {
    }

    /** What one customer owes right now, by how long ago the invoice (or sale order) is. */
    public record ReceivablesAgingRow(Long customerId, String customerName, BigDecimal days0To30, BigDecimal days31To60,
                                      BigDecimal days61To90, BigDecimal over90Days, BigDecimal totalOutstanding) {
    }
}
