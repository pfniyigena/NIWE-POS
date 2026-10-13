package com.posapp.assets;

/** Whether registering an asset also posts its purchase to Accounting, and how it was paid. */
public enum AcquisitionFunding {
    /** Nothing is posted (the purchase is already in the books, or the asset was owned before using this system). */
    NONE,
    /** Debit Fixed Assets, credit Cash. */
    CASH,
    /** Debit Fixed Assets, credit Accounts Payable. */
    PAYABLE
}
