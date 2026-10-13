package com.posapp.user;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Helpers for the grant codes stored on a {@link CustomRole}: a grant is the text {@code <AREA>_<LEVEL>}, for
 * example {@code INVENTORY_MANAGE}. Stored as plain text rather than an enum column so adding an area later never
 * trips a stale database check constraint.
 */
public final class AccessGrants {

    /** Granted to anyone holding at least one valid grant — lets them read shared reference data (products, customers, tax rates...). */
    public static final String ANY_AUTHORITY = "AREA_ANY";

    private AccessGrants() {
    }

    public static String code(AccessArea area, AccessLevel level) {
        return area.name() + "_" + level.name();
    }

    /** Parses one code, or returns null if it is not a valid grant. */
    public static AccessLevel levelOf(String code, AccessArea area) {
        if (code == null) return null;
        for (AccessLevel level : AccessLevel.values()) {
            if (code.equals(code(area, level))) return level;
        }
        return null;
    }

    public static boolean isValid(String code) {
        for (AccessArea area : AccessArea.values()) {
            if (levelOf(code, area) != null) return true;
        }
        return false;
    }

    /** Keeps only valid codes, and drops a VIEW grant when the same area is also MANAGE. */
    public static Set<String> normalize(Collection<String> raw) {
        Set<String> result = new TreeSet<>();
        if (raw == null) return result;
        for (String c : raw) {
            if (c != null && isValid(c.trim())) result.add(c.trim());
        }
        for (AccessArea area : AccessArea.values()) {
            if (result.contains(code(area, AccessLevel.MANAGE))) result.remove(code(area, AccessLevel.VIEW));
        }
        return result;
    }

    /** Every grant code a user effectively holds: a MANAGE grant also counts as VIEW. */
    public static Set<String> effective(Collection<String> grants) {
        Set<String> result = new LinkedHashSet<>();
        if (grants == null) return result;
        for (String c : normalize(grants)) {
            result.add(c);
            for (AccessArea area : AccessArea.values()) {
                if (c.equals(code(area, AccessLevel.MANAGE))) result.add(code(area, AccessLevel.VIEW));
            }
        }
        return result;
    }

    /** The Spring authorities for a set of grants: {@code AREA_<AREA>_<LEVEL>} for each effective grant, plus {@link #ANY_AUTHORITY}. */
    public static Set<String> authorities(Collection<String> grants) {
        Set<String> result = new LinkedHashSet<>();
        for (String c : effective(grants)) result.add("AREA_" + c);
        if (!result.isEmpty()) result.add(ANY_AUTHORITY);
        return result;
    }
}
