package com.posapp.booking;

public enum AppointmentStatus {
    /** Waiting for the deposit or payment; the slot is held until {@code holdExpiresAt}. */
    PENDING_PAYMENT,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
