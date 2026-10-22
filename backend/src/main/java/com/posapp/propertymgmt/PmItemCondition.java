package com.posapp.propertymgmt;

/** The state of one item, best to worst. NA = does not apply here. */
public enum PmItemCondition {
    GOOD, FAIR, POOR, DAMAGED, MISSING, NA;

    /** Worse than the move-in condition: the item has got worse since the tenant arrived. */
    public static boolean worse(PmItemCondition now, PmItemCondition before) {
        if (now == null || before == null || now == NA || before == NA) return false;
        return now.ordinal() > before.ordinal();
    }
}
