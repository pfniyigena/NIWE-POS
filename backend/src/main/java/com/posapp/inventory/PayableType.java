package com.posapp.inventory;

/**
 * Discriminates which kind of document a {@link SupplierPaymentAllocation} (or an
 * outstanding-purchase-order row derived from one) targets — the accounts-payable mirror of
 * {@code com.posapp.credit.OrderType}, now two-way instead of three since this side never grew a
 * legacy middle step. {@code PURCHASE_ORDER} is a payment recorded directly against a {@link
 * PurchaseOrder} — every allocation ever recorded before Fourier's "same workflow as Sale order"
 * request, kept working unchanged (see {@code SupplierPaymentService.amountOwedForOrder}), and
 * still the target for any purchase order that owes money before it has a {@code
 * com.posapp.bill.Bill} of its own (e.g. a SENT order nothing has arrived against yet). {@code
 * BILL} is the replacement once one exists — a {@code com.posapp.bill.Bill}, generated from this
 * order — {@code SupplierPaymentService.listOutstandingOrders} offers the Bill instead of the
 * Purchase Order directly the moment one exists, exactly mirroring how {@code
 * CustomerCreditService.listOutstandingOrders} offers an Invoice instead of its Sales Order.
 */
public enum PayableType {
    PURCHASE_ORDER,
    BILL
}
