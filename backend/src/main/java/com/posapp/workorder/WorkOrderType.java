package com.posapp.workorder;

/** Who the job is for. */
public enum WorkOrderType {
    /** A paid job for a customer: billed on an invoice. */
    CUSTOMER,
    /** Maintenance or repair of the business's own equipment or premises: costs are tracked, nothing is billed. */
    INTERNAL
}
