package com.posapp.order;

public enum OrderStatus {
    /** A cart in progress — the actively-viewed order or one of its sibling "parked" tabs. */
    OPEN,
    COMPLETED,
    VOIDED
}
