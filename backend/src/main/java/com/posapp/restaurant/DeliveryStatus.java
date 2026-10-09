package com.posapp.restaurant;

/**
 * Hand-off lifecycle for a {@code RestaurantOrder} with {@code orderType == DELIVERY} — only ever
 * set on delivery orders; deliberately separate from {@link RestaurantOrderStatus}, which tracks
 * whether the order/bill itself is OPEN/CLOSED/CANCELLED (payment can be collected before, during,
 * or after the hand-off, so the two lifecycles are independent).
 */
public enum DeliveryStatus {
    AWAITING_DRIVER, OUT_FOR_DELIVERY, DELIVERED
}
