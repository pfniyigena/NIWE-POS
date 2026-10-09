package com.posapp.restaurant;

/**
 * Fourier: "Restaurant wastage should be confirmed by admin once confirmed inventory and account
 * will be impacted." A {@link RestaurantWastage} entry's own lifecycle — see that class's javadoc
 * and {@code RestaurantWastageService.confirm}/{@code reject} for who moves it and when.
 */
public enum RestaurantWastageStatus {
    /** Logged by staff, not yet reviewed — stock and accounting are untouched at this point. */
    PENDING,
    /** An admin reviewed and approved it — stock was decremented and the loss was posted to Accounting at that moment. */
    CONFIRMED,
    /** An admin reviewed and rejected it (e.g. logged in error) — stock and accounting were never touched. */
    REJECTED
}
