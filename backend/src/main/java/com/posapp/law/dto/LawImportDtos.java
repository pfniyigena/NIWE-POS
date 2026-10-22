package com.posapp.law.dto;

import java.util.List;

/** Importing clients and open matters from a spreadsheet. Row numbers are the spreadsheet's own (the heading row is row 1). */
public final class LawImportDtos {

    private LawImportDtos() {
    }

    /** One row that was skipped (with the reason) or that needs a word of warning. */
    public record ImportRow(int row, String label, String message) {
    }

    public record ImportResult(String kind, boolean dryRun, String fileName, int rows, int ok, int skipped, int clientsAdded, List<ImportRow> problems, List<ImportRow> notes, List<String> sample,
                               List<String> columns, List<String> ignoredColumns, String message) {
    }
}
