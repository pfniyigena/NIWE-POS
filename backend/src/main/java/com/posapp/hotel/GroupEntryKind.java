package com.posapp.hotel;

/** BILL: a member's bill moved to the group at check-out. DEPOSIT: money received and held. APPLIED: held deposit set against bills. PAYMENT: money received against bills. REFUND: held deposit given back. */
public enum GroupEntryKind {
    BILL, DEPOSIT, APPLIED, PAYMENT, REFUND
}
