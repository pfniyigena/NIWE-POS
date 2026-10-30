package com.posapp.installment;

/** What kind of contact a collections note records. PROMISE carries a date (and optionally an amount) the customer said they would pay by. */
public enum InstallmentFollowUpKind {
    CALL,
    VISIT,
    EMAIL,
    PROMISE,
    NOTE
}
