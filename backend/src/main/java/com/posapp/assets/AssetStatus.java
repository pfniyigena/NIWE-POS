package com.posapp.assets;

public enum AssetStatus {
    /** In use and still being depreciated. */
    ACTIVE,
    /** Depreciated down to its salvage value — still owned, no more depreciation. */
    FULLY_DEPRECIATED,
    /** Sold or written off. */
    DISPOSED
}
