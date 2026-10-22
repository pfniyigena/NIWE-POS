package com.posapp.order;

/**
 * Payment method codes the till handles itself. They are not rows in the payment-methods list: a gift card or loyalty-points
 * payment has to be tied to a card or a customer, so other screens (restaurant, hotel, refunds) can never take them by accident.
 */
public final class Tenders {

    /** Paid from a gift card; the payment row carries the card. Money the store owed (Gift Card Liability) becomes sales. */
    public static final String GIFT_CARD = "GIFT_CARD";
    /** Paid with loyalty points; the payment row carries the points. The value is a rewards expense. */
    public static final String LOYALTY = "LOYALTY";

    private Tenders() {
    }

    public static boolean isSpecial(String method) {
        return GIFT_CARD.equals(method) || LOYALTY.equals(method);
    }
}
