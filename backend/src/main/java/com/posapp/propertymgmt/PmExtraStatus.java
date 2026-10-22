package com.posapp.propertymgmt;

/** A reading or one-off charge waits (PENDING) for the next rent invoice of the lease, then is BILLED on it; a pending one can be CANCELLED. */
public enum PmExtraStatus {
    PENDING, BILLED, CANCELLED
}
