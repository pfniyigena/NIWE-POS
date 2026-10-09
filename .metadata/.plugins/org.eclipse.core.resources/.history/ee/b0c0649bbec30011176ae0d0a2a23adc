package com.posapp.tracking;

/** How a product's individual units are traced. NONE = plain quantity only. */
public enum TrackingMode {
    NONE,
    /** Units belong to batches/lots, optionally with an expiry date; sold earliest-expiry-first. */
    BATCH,
    /** Every unit has its own serial number. */
    SERIAL;

    public static TrackingMode of(String value) {
        if (value == null || value.isBlank()) return NONE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw com.posapp.common.ApiException.badRequest("Unknown tracking mode '" + value + "' — use NONE, BATCH or SERIAL");
        }
    }
}
