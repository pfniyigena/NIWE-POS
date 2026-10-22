package com.posapp.propertymgmt;

/** Why part of a deposit was kept. UNPAID_RENT settles a rent invoice; the others are charges for what the tenant left behind. */
public enum PmDepositReason {
    UNPAID_RENT, DAMAGE, CLEANING, UTILITIES, OTHER
}
