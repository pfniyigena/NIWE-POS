package com.posapp.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A plain, stable JSON envelope for any paged admin listing (Task: "check the loading of 100000
 * products" — the first place this codebase needed real server-side paging). Deliberately not
 * Spring's {@code Page<T>} serialized directly: that type's JSON shape carries internal Spring
 * Data fields (a nested "pageable", "sort", etc.) that aren't part of this app's API contract and
 * could change out from under the frontend on a Spring upgrade. This wraps just the fields a
 * paged table actually needs.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
