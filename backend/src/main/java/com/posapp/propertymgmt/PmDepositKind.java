package com.posapp.propertymgmt;

/** One movement of a lease's security deposit. RECEIVED and TRANSFER_IN put money in; DEDUCTION, REFUND and TRANSFER_OUT take it out. */
public enum PmDepositKind {
    RECEIVED, TRANSFER_IN, DEDUCTION, REFUND, TRANSFER_OUT;

    public boolean in() {
        return this == RECEIVED || this == TRANSFER_IN;
    }
}
