package com.posapp.promotions;

public enum PromotionType {
    /** value% off. */
    PERCENT_OFF,
    /** value off each unit (PRODUCT/CATEGORY) or off the whole cart (CART). */
    AMOUNT_OFF,
    /** Buy {@code buyQuantity}, get {@code freeQuantity} free, repeating. */
    BUY_X_GET_Y
}
