package com.posapp.bill;

/**
 * A bill's lifecycle — Fourier: "The Purchase order should flow same workflow as Sale order (with
 * Bills and Refunds equivalent with Invoices and Credit note)." Deliberate line-for-line mirror of
 * {@code com.posapp.invoice.InvoiceStatus}, scoped down to this app's actual need: every {@link
 * Bill} is generated from a {@code com.posapp.inventory.PurchaseOrder} (there is no standalone,
 * "no purchase order behind it" bill the way a standalone {@code Invoice} exists — Fourier never
 * asked for that, and a bill with nothing ordered/received behind it has no stock event to anchor
 * it to) — see {@code BillService.buildFromPurchaseOrder}'s two callers for the automatic-vs-manual
 * split, same as {@code InvoiceService.generateDraftFromSalesOrder}/{@code generate}. A bill
 * generated once the order's {@code PurchaseOrderStatus} is {@code RECEIVED} starts as {@code
 * DRAFT} (an admin gets a chance to review it, same as the sales side's auto-draft-on-fulfillment);
 * the manual "Generate bill" action (available from {@code SENT} onward, same floor as {@code
 * InvoiceService.buildFromSalesOrder}'s own DRAFT/CANCELLED guard) creates an already-{@code
 * ISSUED} one directly. From {@code ISSUED}, the only further move is to {@code VOID} (see {@link
 * Bill#refundNumber}'s javadoc) — refused once anything has been paid against it, same as {@code
 * InvoiceService.voidInvoice}'s guard; a paid bill is cancelled via a Purchase Return instead (the
 * purchase-side mirror of a Sales Order Return), never via this document.
 */
public enum BillStatus {
    DRAFT,
    ISSUED,
    VOID
}
