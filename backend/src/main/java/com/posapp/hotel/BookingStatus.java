package com.posapp.hotel;

/** Life of a hotel booking: BOOKED (reserved) -> CHECKED_IN -> CHECKED_OUT, or CANCELLED / NO_SHOW before the guest arrives. */
public enum BookingStatus {
    BOOKED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW
}
