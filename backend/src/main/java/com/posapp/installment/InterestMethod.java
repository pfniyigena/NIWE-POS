package com.posapp.installment;

/** NONE: no interest. FLAT: the yearly rate is charged on the whole financed amount for the length of the plan and split evenly. REDUCING: the rate is charged on what is still owed (equal total instalments, like a loan). */
public enum InterestMethod {
    NONE,
    FLAT,
    REDUCING
}
