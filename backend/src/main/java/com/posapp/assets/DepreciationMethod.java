package com.posapp.assets;

/** How an asset loses value month by month. */
public enum DepreciationMethod {
    /** The same amount every month: (cost - salvage value) / useful life in months. */
    STRAIGHT_LINE,
    /** A fixed share of the remaining book value every month (double the straight-line rate), never below the salvage value. Bigger charges early on. */
    DECLINING_BALANCE
}
