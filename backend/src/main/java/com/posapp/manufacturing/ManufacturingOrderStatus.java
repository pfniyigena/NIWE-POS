package com.posapp.manufacturing;

/**
 * A Manufacturing Order's lifecycle — the same DRAFT-then-locked, run-based shape {@code
 * PurchaseOrderStatus}/{@code SalesOrderStatus} already use. DRAFT is freely editable (change the
 * Bom, planned quantity, note); {@link ManufacturingOrderService#confirm} locks it and snapshots
 * its routing; only {@link ManufacturingOrderService#produce} and {@link
 * ManufacturingOrderService#cancel} can still move it after that. PARTIALLY_PRODUCED/PRODUCED are
 * set automatically by {@code produce} from how much has actually been produced so far, never
 * chosen directly by the admin.
 */
public enum ManufacturingOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_PRODUCED,
    PRODUCED,
    CANCELLED
}
