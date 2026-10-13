package com.posapp.hotel;

/** Cleaning state of a room: DIRTY after a guest leaves, CLEANING while housekeeping is in it, INSPECTED once checked, CLEAN when ready. */
public enum HousekeepingStatus {
    CLEAN, DIRTY, CLEANING, INSPECTED
}
