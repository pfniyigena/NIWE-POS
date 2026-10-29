package com.posapp.workorder;

import java.time.LocalDate;

/** How often a preventive maintenance schedule comes round. */
public enum IntervalUnit {
    DAYS, WEEKS, MONTHS;

    public LocalDate addTo(LocalDate date, int count) {
        return switch (this) {
            case DAYS -> date.plusDays(count);
            case WEEKS -> date.plusWeeks(count);
            case MONTHS -> date.plusMonths(count);
        };
    }
}
