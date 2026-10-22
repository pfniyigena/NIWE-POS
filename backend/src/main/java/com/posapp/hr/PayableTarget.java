package com.posapp.hr;

/** Which account a deduction or contribution is owed in: social security (RSSB / CNPS), other payroll payables (health insurance, payroll tax...), or STAFF_ADVANCE (a loan repayment, which reduces what the employee owes the company rather than being paid to a third party). */
public enum PayableTarget {
    SOCIAL_SECURITY, OTHER, STAFF_ADVANCE
}
