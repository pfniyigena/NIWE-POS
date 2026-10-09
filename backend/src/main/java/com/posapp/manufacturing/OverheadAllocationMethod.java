package com.posapp.manufacturing;

/**
 * How one {@link OverheadCost} pool's {@code rate} turns into an actual amount charged against a
 * Manufacturing Order run (Fourier: "Indirect Costs", confirmed as named overhead pools each with
 * their own allocation rule rather than one flat store-wide overhead %). See {@code
 * ManufacturingOrderService}'s production step for exactly how each method is applied.
 */
public enum OverheadAllocationMethod {
    /**
     * {@code rate} is a fixed total for the whole order, spread across its runs in proportion to
     * how much of the order's planned quantity each run produced — so the sum across every run of
     * a fully-produced order adds up to exactly {@code rate}, no more, no less.
     */
    FLAT,
    /** {@code rate} is charged per unit actually produced in a run — {@code rate * quantityThisRun}. */
    PER_UNIT,
    /** {@code rate} is a percentage of that run's own material cost — {@code rate% * materialCostThisRun}. */
    PERCENT_OF_MATERIAL_COST
}
