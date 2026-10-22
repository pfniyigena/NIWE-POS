package com.posapp.law;

public enum StageStatus {
    /** Agreed, not billed yet. */
    PLANNED,
    /** On an invoice (or released again when that invoice goes). */
    BILLED
}
