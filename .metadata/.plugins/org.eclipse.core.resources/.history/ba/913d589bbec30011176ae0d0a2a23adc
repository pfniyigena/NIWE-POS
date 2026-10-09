package com.posapp.inventory.dto;

import com.posapp.inventory.PayableType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of a supplier's outstanding-payables list — backs the Record Supplier Payment screen's
 * allocation table, the accounts-payable mirror of {@code com.posapp.credit.dto.OutstandingOrderDto}.
 * Returned oldest-first (see {@code SupplierPaymentService.listOutstandingOrders}'s ordering), so
 * the frontend can build the same "oldest-first" default allocation suggestion the customer-payment
 * screen already offers.
 *
 * <p>Fourier: "The Purchase order should flow same workflow as Sale order (with Bills ... equivalent
 * with Invoices)" — {@code payableType} tells the frontend what {@code purchaseOrderId} actually
 * targets (own purchase order, or a {@code com.posapp.bill.Bill} generated from it — see {@code
 * PayableType}'s own javadoc); {@code orderNumber} carries whichever document's own identifier
 * applies ({@code PurchaseOrder.orderNumber} or {@code Bill.billNumber}), same "one field, whichever
 * number applies" shape {@code OutstandingOrderDto.invoiceNumber} already uses.
 */
public record OutstandingPurchaseOrderDto(
        Long purchaseOrderId,
        PayableType payableType,
        String orderNumber,
        Instant orderDate,
        Instant expectedDate,
        BigDecimal totalCost,
        BigDecimal amountOutstanding
) {
}
