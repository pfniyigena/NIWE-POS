package com.posapp.inventory;

/**
 * Task: "Inventory depth ... stock adjustments/write-offs with a reason code." A fixed, curated
 * set rather than free text — same "closed vocabulary so reports/filters stay meaningful" reasoning
 * as AuditLog's own curated action codes. OTHER exists as an escape hatch for anything that
 * doesn't fit the other four; {@code StockAdjustmentRequest.note} is where the specifics go either
 * way.
 *
 * <p>Fourier: "when creating product if the stock quantity is set then it should impact inventory
 * of cause [sic] displayed into Stock Movement History" — {@link #INITIAL_STOCK} is system-set
 * only, by {@code StockAdjustmentService#recordInitialStock}, never offered on
 * {@code StockAdjustmentRequest}/the manual "New stock adjustment" form's own reason dropdown
 * (that list is hardcoded in {@code StockAdjustmentFormComponent}, not driven off this enum, so
 * adding a value here doesn't surface it there) — picking "initial stock" for an ordinary
 * correction wouldn't mean anything.
 */
public enum StockAdjustmentReason {
    DAMAGE,
    THEFT,
    EXPIRY,
    RECOUNT,
    OTHER,
    INITIAL_STOCK
}
