package com.posapp.restaurant;

/**
 * Fourier: "Restaurants need a dedicated wastage module." A fixed, curated set rather than free
 * text — same "closed vocabulary so reports/filters stay meaningful" reasoning as {@code
 * StockAdjustmentReason}/{@code AuditLog}'s own curated codes — but its own vocabulary, not that
 * enum's, since none of DAMAGE/THEFT/EXPIRY/RECOUNT quite fits "the kitchen burnt a batch" or "a
 * server dropped a plate." {@code RestaurantWastageRequest.note} is where the specifics go either
 * way; OTHER is the escape hatch for anything that doesn't fit the rest.
 */
public enum RestaurantWastageReason {
    SPOILAGE,
    BURNT_OR_OVERCOOKED,
    DROPPED_OR_SPILLED,
    EXPIRED,
    PREP_TRIM,
    CUSTOMER_COMPLAINT,
    OTHER
}
