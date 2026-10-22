package com.posapp.giftcards;

public enum GiftCardTxnType {
    /** The card was sold (or issued free) with this amount on it. */
    SOLD,
    /** Money added to a card that already existed. */
    TOPUP,
    /** Spent at the till (a negative amount). */
    REDEEM,
    /** Given back after a return on a sale that was paid with this card. */
    REFUND,
    /** The balance left on an expired card was moved out of what the store owes (a negative amount). */
    RELEASED
}
