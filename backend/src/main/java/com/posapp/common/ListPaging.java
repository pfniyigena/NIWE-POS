package com.posapp.common;

import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Shared helper for the core list screens (invoices, quotations, orders, bills, payments,
 * customers, suppliers): optional search / status / date / branch filters plus optional paging.
 *
 * <p>Compatibility rule: with no {@code size} parameter the whole (filtered) list is returned, as
 * before, so every existing caller keeps working. With {@code size} the caller gets one page and the
 * full filtered count in the {@code X-Total-Count} response header.
 */
public final class ListPaging {

    public static final int MAX = 500;
    public static final String TOTAL = "X-Total-Count";

    private ListPaging() {
    }

    public static <T> ResponseEntity<List<T>> of(List<T> all, Integer page, Integer size) {
        int total = all.size();
        List<T> out = all;
        if (size != null) {
            int s = Math.max(1, Math.min(size, MAX));
            int p = page == null ? 0 : Math.max(0, page);
            long from = (long) p * s;
            out = from >= total ? List.of() : all.subList((int) from, (int) Math.min(total, from + s));
        }
        return ResponseEntity.ok().header(TOTAL, String.valueOf(total)).body(out);
    }

    /** True when {@code q} is blank, or any of the given fields contains it (case-insensitive). */
    public static boolean has(String q, String... fields) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.trim().toLowerCase();
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code filter} is blank, or equals {@code value} ignoring case. */
    public static boolean is(String filter, String value) {
        return filter == null || filter.isBlank() || (value != null && value.equalsIgnoreCase(filter.trim()));
    }

    public static boolean sameId(Long filter, Long value) {
        return filter == null || filter.equals(value);
    }

    /** Date range test on an instant, in the server's zone; either end may be null; a null instant fails a set range. */
    public static boolean between(Instant t, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (t == null) {
            return false;
        }
        LocalDate d = t.atZone(ZoneId.systemDefault()).toLocalDate();
        return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    /** A record with no branch belongs to the headquarters branch. No filter means everything. */
    public static boolean branch(Long recordBranchId, Long filterBranchId, Long headquartersId) {
        if (filterBranchId == null) {
            return true;
        }
        Long eff = recordBranchId != null ? recordBranchId : headquartersId;
        return filterBranchId.equals(eff);
    }
}
