package com.posapp.hotel;

/**
 * When room revenue reaches Accounting.
 * NIGHTLY: each night's room charge is posted as revenue (and owed by the in-house guest) by the night audit.
 * AT_CHECKOUT: nothing for the room until the stay ends, then the whole stay is posted at check-out.
 * BOOKING_AND_CHECKOUT: the booked value is recorded as unearned revenue when the booking is made and becomes revenue at check-out.
 * Deposits, payments and refunds always post when the money moves; restaurant and POS charges post in their own modules.
 */
public enum PostingMode {
    NIGHTLY, AT_CHECKOUT, BOOKING_AND_CHECKOUT
}
