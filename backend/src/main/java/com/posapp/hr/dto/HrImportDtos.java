package com.posapp.hr.dto;

import java.util.List;

/** Importing employees from a spreadsheet. Row numbers are the spreadsheet's own (the heading row is row 1). */
public final class HrImportDtos {

    private HrImportDtos() {
    }

    public record ImportRow(int row, String label, String message) {
    }

    public record ImportResult(boolean dryRun, String fileName, int rows, int ok, int skipped, List<ImportRow> problems, List<ImportRow> notes, List<String> sample,
                               List<String> columns, List<String> ignoredColumns, String message) {
    }
}
