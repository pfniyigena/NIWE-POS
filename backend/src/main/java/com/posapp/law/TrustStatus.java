package com.posapp.law;

/** A payout or fee transfer waits for a second person; everything else is posted at once. */
public enum TrustStatus {
    PENDING, POSTED, REJECTED, CANCELLED
}
