package com.posapp.timetracking;

/** Where a time entry is in its life: written, handed in, decided on, billed. */
public enum TimeEntryStatus {
    /** Written by the staff member; still theirs to edit or delete. */
    DRAFT,
    /** Handed in for approval. */
    SUBMITTED,
    /** Approved (or approval is switched off). Billable approved time can be put on an invoice. */
    APPROVED,
    /** Sent back with a reason; the staff member corrects it and submits again. */
    REJECTED,
    /** Already put on an invoice. */
    INVOICED
}
