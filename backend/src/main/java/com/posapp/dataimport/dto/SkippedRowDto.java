package com.posapp.dataimport.dto;

/** One CSV row BulkImportService couldn't import, and why — rowNumber is 1-based and counts only data rows (the header row itself is never number 1), matching how a spreadsheet's own row numbers would look once the header is subtracted out. */
public record SkippedRowDto(int rowNumber, String reason) {
}
