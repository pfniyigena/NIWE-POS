package com.posapp.loyalty;

public enum LoyaltyTxnType {
    /** Points earned on a sale. */
    EARNED,
    /** Points spent as a payment on a sale. */
    REDEEMED,
    /** Points taken back because the goods that earned them were returned. */
    EARN_REVERSED,
    /** Points given back because a return refunded the part of the sale that points paid for. */
    REDEEM_REFUNDED,
    /** Added or removed by hand by an administrator. */
    ADJUSTED
}
