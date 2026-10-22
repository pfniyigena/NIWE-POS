package com.posapp.realestate;

import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Optional paging for the list screens. Without {@code size} the whole list is returned, as before, so the pickers and reports that need every row keep working. With
 * {@code size} (1 to {@link #MAX}) the list is cut to one page ({@code page} counts from 0). Either way {@code X-Total-Count} tells how many rows matched in all.
 */
public final class RePaging {

    public static final int MAX = 500;
    public static final String TOTAL = "X-Total-Count";

    private RePaging() {}

    public static <T> ResponseEntity<List<T>> of(List<T> all, Integer page, Integer size) {
        if (size == null) return ResponseEntity.ok().header(TOTAL, String.valueOf(all.size())).body(all);
        int sz = Math.max(1, Math.min(size, MAX));
        int pg = Math.max(0, page == null ? 0 : page);
        long from = (long) pg * sz;
        List<T> part = from >= all.size() ? List.of() : all.subList((int) from, (int) Math.min(all.size(), from + sz));
        return ResponseEntity.ok().header(TOTAL, String.valueOf(all.size())).body(part);
    }
}
