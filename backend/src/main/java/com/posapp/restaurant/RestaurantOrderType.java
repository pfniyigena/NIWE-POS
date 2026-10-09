package com.posapp.restaurant;

/**
 * Fourier: "Delivery & takeaway" — DINE_IN is the original table-tab flow ({@code table} is
 * required); TAKEAWAY and DELIVERY are off-premise orders opened with no table (a customer
 * name/phone stand in for "which table" when routing a Kitchen Display ticket or a payment).
 * DELIVERY additionally carries an address and a hand-off lifecycle — see {@link DeliveryStatus}.
 */
public enum RestaurantOrderType {
    DINE_IN, TAKEAWAY, DELIVERY
}
