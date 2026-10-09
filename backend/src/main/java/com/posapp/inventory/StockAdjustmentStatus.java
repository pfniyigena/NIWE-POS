package com.posapp.inventory;

/**
 * Fourier: "Build a full permissions system" — Stock Adjustments now mirror
 * {@code com.posapp.restaurant.RestaurantWastageStatus}'s own PENDING/CONFIRMED/REJECTED
 * lifecycle, closing the gap the user called out: previously a {@code create} had immediate
 * effect, with no review step at all. See {@link StockAdjustment}'s own javadoc and
 * {@code StockAdjustmentService.confirm}/{@code reject} for who moves it and when.
 */
public enum StockAdjustmentStatus {
    /** Logged by staff, not yet reviewed — the real stock/accounting impact hasn't happened yet. */
    PENDING,
    /** An admin (or a PERM_APPROVE_STOCK_ADJUSTMENT holder) reviewed and approved it — stock was adjusted and the change was posted to Accounting at that moment. */
    CONFIRMED,
    /** Reviewed and rejected (e.g. logged in error) — stock and accounting were never touched. */
    REJECTED
}
