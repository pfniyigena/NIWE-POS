package com.posapp.restaurant;

import com.posapp.common.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The allergen tags a menu item or a guest can carry. */
public final class RestaurantAllergens {

    public static final List<String> ALL = List.of("GLUTEN", "DAIRY", "EGGS", "PEANUTS", "NUTS", "SOY", "FISH", "SHELLFISH", "SESAME", "CELERY", "MUSTARD");

    private RestaurantAllergens() {
    }

    /** Checks a comma separated list against {@link #ALL}; returns it upper-cased, without repeats, in the standard order; null when empty. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Set<String> seen = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String t = part.trim().toUpperCase();
            if (t.isEmpty()) continue;
            if (!ALL.contains(t)) throw ApiException.badRequest("Unknown allergen: " + part.trim() + ". Choose from " + String.join(", ", ALL));
            seen.add(t);
        }
        if (seen.isEmpty()) return null;
        List<String> ordered = new ArrayList<>();
        for (String a : ALL) if (seen.contains(a)) ordered.add(a);
        return String.join(",", ordered);
    }

    public static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String p : csv.split(",")) if (!p.isBlank()) out.add(p.trim());
        return out;
    }

    /** The allergens present in both lists. */
    public static List<String> overlap(String a, String b) {
        List<String> bs = split(b);
        List<String> out = new ArrayList<>();
        for (String x : split(a)) if (bs.contains(x)) out.add(x);
        return out;
    }
}
