package com.posapp.restaurant;

/** Per-line Kitchen Order Ticket lifecycle (Fourier: "Kitchen Order Tickets / Kitchen Display") — see RestaurantOrderLine.kitchenStatus's javadoc. */
public enum KitchenStatus {
    PENDING, SENT, PREPARING, READY, SERVED
}
