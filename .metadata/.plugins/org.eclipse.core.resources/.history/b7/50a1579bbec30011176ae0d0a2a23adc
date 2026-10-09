package com.posapp.inventory;

/**
 * Life cycle of a stock count: {@code IN_PROGRESS} (sheet generated, quantities being entered) ->
 * {@code SUBMITTED} (counting finished, waiting for sign-off) -> {@code APPROVED} (differences posted as
 * stock adjustments). A count that is no longer wanted is {@code CANCELLED}; nothing was posted.
 */
public enum StockCountStatus {
    IN_PROGRESS,
    SUBMITTED,
    APPROVED,
    CANCELLED
}
