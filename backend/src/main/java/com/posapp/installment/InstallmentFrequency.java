package com.posapp.installment;

import java.time.LocalDate;

/** How far apart the instalments fall. */
public enum InstallmentFrequency {
    WEEKLY(52),
    BIWEEKLY(26),
    MONTHLY(12),
    QUARTERLY(4);

    private final int perYear;

    InstallmentFrequency(int perYear) {
        this.perYear = perYear;
    }

    public int perYear() {
        return perYear;
    }

    /** The due date of the {@code index}-th instalment (0 = the first one). Counted from the first date so a 31st doesn't drift. */
    public LocalDate dueDate(LocalDate first, int index) {
        return switch (this) {
            case WEEKLY -> first.plusWeeks(index);
            case BIWEEKLY -> first.plusWeeks(2L * index);
            case MONTHLY -> first.plusMonths(index);
            case QUARTERLY -> first.plusMonths(3L * index);
        };
    }
}
