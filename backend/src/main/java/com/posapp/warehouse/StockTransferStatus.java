package com.posapp.warehouse;

/**
 * Fourier: "multi-location/multi-warehouse tracking ... Stock Transfers" — mirrors the
 * PENDING/CONFIRMED/REJECTED lifecycle {@code com.posapp.inventory.StockAdjustmentStatus}/{@code
 * com.posapp.restaurant.RestaurantWastageStatus} already use, per Fourier's own choice of an
 * approval step over applying a transfer immediately. See {@link StockTransfer}'s own javadoc.
 */
public enum StockTransferStatus {
    /** Logged, not yet reviewed — neither warehouse's stock has moved yet. */
    PENDING,
    /** An admin (or a PERM_APPROVE_STOCK_TRANSFER holder) reviewed and approved it — stock moved between the two warehouses at that moment. */
    CONFIRMED,
    /** Reviewed and rejected (e.g. logged in error) — neither warehouse's stock was ever touched. */
    REJECTED,
    /** Inter-branch only: dispatched (stock has left the source) but not yet received at the destination. */
    IN_TRANSIT,
    /** Inter-branch only: received at the destination (possibly short). */
    RECEIVED
}
