package com.posapp.law;

/** What a client-money movement is. Only a deposit puts money in; the others take it out. */
public enum TrustType {
    /** Money received from or for the client, paid into the trust account. */
    DEPOSIT,
    /** Money paid out of the trust account to the client or to someone on the client's behalf. */
    PAYOUT,
    /** Money moved from the trust account to the firm to pay an issued invoice. */
    FEE_TRANSFER,
    /** A deposit that was entered wrongly or bounced, taken back out. */
    DEPOSIT_REVERSAL
}
