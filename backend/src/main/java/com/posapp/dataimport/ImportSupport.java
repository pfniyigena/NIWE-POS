package com.posapp.dataimport;

import com.posapp.common.ApiException;
import com.posapp.dataimport.dto.ImportResultDto;
import com.posapp.dataimport.dto.SkippedRowDto;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CSV reading, cell parsing and result building shared by the importers. */
final class ImportSupport {

    /** How many accepted rows a result describes (the rest are only counted). */
    static final int ACCEPTED_LIMIT = 200;

    private ImportSupport() {
    }

    static List<Map<String, String>> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a CSV file to import");
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).setIgnoreEmptyLines(true).build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8); CSVParser parser = format.parse(reader)) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) rows.add(record.toMap());
            return rows;
        } catch (IOException | IllegalArgumentException e) {
            throw ApiException.badRequest("Failed to read the uploaded CSV file: " + e.getMessage());
        }
    }

    /** The file must contain these columns (any order, any case); a missing one fails the whole file with a clear message instead of every row. */
    static void requireColumns(List<Map<String, String>> rows, String... columns) {
        if (rows.isEmpty()) return;
        Map<String, String> first = rows.get(0);
        for (String c : columns) {
            boolean found = first.keySet().stream().anyMatch(k -> k != null && k.trim().equalsIgnoreCase(c));
            if (!found) throw ApiException.badRequest("The file has no \"" + c + "\" column. Download the template to see the columns.");
        }
    }

    static String text(Map<String, String> row, String column) {
        String value = get(row, column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String get(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v != null) return v;
        for (Map.Entry<String, String> e : row.entrySet()) if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(column)) return e.getValue();
        return null;
    }

    static String requireText(Map<String, String> row, String column) {
        String value = text(row, column);
        if (value == null) throw ApiException.badRequest(column + " is required");
        return value;
    }

    static BigDecimal requireDecimal(Map<String, String> row, String column) {
        String value = text(row, column);
        if (value == null) throw ApiException.badRequest(column + " is required");
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(column + " is not a valid number: '" + value + "'");
        }
    }

    static LocalDate requireDate(Map<String, String> row, String column) {
        String value = text(row, column);
        if (value == null) throw ApiException.badRequest(column + " is required (write it as 2026-01-31)");
        return parseDate(column, value);
    }

    static LocalDate dateOrNull(Map<String, String> row, String column) {
        String value = text(row, column);
        return value == null ? null : parseDate(column, value);
    }

    private static LocalDate parseDate(String column, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(column + " is not a valid date: '" + value + "' (write it as 2026-01-31)");
        }
    }

    static boolean boolOrDefault(Map<String, String> row, String column, boolean fallback) {
        String value = text(row, column);
        if (value == null) return fallback;
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw ApiException.badRequest(column + " is not a valid true/false value: '" + value + "'");
        };
    }

    static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    /** Builds the result; a dry run marks the surrounding transaction rollback-only so nothing is kept. */
    static ImportResultDto result(boolean dryRun, int total, int imported, List<SkippedRowDto> skipped, List<SkippedRowDto> accepted, List<String> notes) {
        if (dryRun) TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        List<String> n = new ArrayList<>(notes);
        List<SkippedRowDto> shown = accepted;
        if (accepted.size() > ACCEPTED_LIMIT) {
            shown = new ArrayList<>(accepted.subList(0, ACCEPTED_LIMIT));
            n.add("Only the first " + ACCEPTED_LIMIT + " of " + accepted.size() + " good rows are listed.");
        }
        return new ImportResultDto(imported, skipped.size(), skipped, dryRun, total, shown, n);
    }
}
