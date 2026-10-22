package com.posapp.dataimport.dto;

import java.util.List;

/**
 * The outcome of an import, or of checking one first. Partial success by design: {@code imported} rows went in (or, when {@code dryRun}, would go in), {@code skippedRows}
 * lists every row that did not and why. A dry run changes nothing: the file is processed for real and then everything is rolled back, so the answer is exactly what the
 * real import would do. {@code accepted} describes the first rows that are fine (what will be created); {@code notes} carries side effects such as new categories.
 */
public record ImportResultDto(int imported, int skipped, List<SkippedRowDto> skippedRows, boolean dryRun, int total, List<SkippedRowDto> accepted, List<String> notes) {

    public ImportResultDto(int imported, int skipped, List<SkippedRowDto> skippedRows) {
        this(imported, skipped, skippedRows, false, imported + skipped, List.of(), List.of());
    }
}
