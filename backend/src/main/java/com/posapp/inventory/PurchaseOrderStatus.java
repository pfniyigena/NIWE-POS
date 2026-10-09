package com.posapp.inventory;

/**
 * A purchase order's lifecycle (Task: "Inventory depth ... purchase orders and ... a
 * stock-receiving flow that increments quantity with a paper trail"). DRAFT is freely editable
 * (add/remove lines, change quantities); everything from SENT onward is locked to edits — only
 * {@link PurchaseOrderService#receiveLines} and {@link PurchaseOrderService#cancel} can still move
 * it. PARTIALLY_RECEIVED/RECEIVED are set automatically by receiveLines from how much of each
 * line has arrived so far, never chosen directly by the admin.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    SENT,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}
