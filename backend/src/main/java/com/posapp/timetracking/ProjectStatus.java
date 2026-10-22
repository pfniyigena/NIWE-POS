package com.posapp.timetracking;

public enum ProjectStatus {
    /** Time can be logged. */
    ACTIVE,
    /** Finished: no new time, still reported and billable. */
    COMPLETED,
    /** Hidden from the pickers and the lists by default. */
    ARCHIVED
}
