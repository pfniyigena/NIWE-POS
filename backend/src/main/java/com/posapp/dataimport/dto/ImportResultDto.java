package com.posapp.dataimport.dto;

import java.util.List;

/**
 * Fourier: "I need to bulk-load my existing product list" — and per Fourier's own answer on how
 * a conflicting row should be handled ("skip conflicting rows, import the rest"), this is always
 * a partial-success shape rather than an all-or-nothing one: {@code imported} rows went in,
 * {@code skippedRows} lists every row that didn't along with why, and the caller sees both —
 * nothing in a bulk import ever silently fails.
 */
public record ImportResultDto(int imported, int skipped, List<SkippedRowDto> skippedRows) {
}
