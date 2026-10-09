package com.posapp.restaurant;

/** OPEN -&gt; CLOSED (paid) or CANCELLED — mirrors SaleOrder/Quotation's own simple status lifecycle. */
public enum RestaurantOrderStatus {
    OPEN, CLOSED, CANCELLED
}
