package com.posapp.hr;

/** Only an ACTIVE contract is paid by payroll (expired / terminated ones still count for the part of a month they covered). */
public enum ContractStatus {
    DRAFT, ACTIVE, EXPIRED, TERMINATED
}
