package com.posapp.propertymgmt;

/** How often the tenant pays the rent. */
public enum RentFrequency {
    MONTHLY(1), QUARTERLY(3), HALF_YEARLY(6), ANNUAL(12);

    private final int months;

    RentFrequency(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
