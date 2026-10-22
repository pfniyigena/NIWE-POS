package com.posapp.common;

import com.posapp.reports.ReportExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Builds the Excel / PDF / CSV download for a table of text cells; shared by the list exports. */
public final class ExportFiles {

    private ExportFiles() {
    }

    public static ResponseEntity<byte[]> file(ReportExportService exportService, String title, String fileBase, List<String> headers, List<List<String>> body, String format) {
        byte[] content;
        MediaType type;
        String ext;
        if ("csv".equalsIgnoreCase(format)) {
            content = csv(headers, body);
            type = MediaType.parseMediaType("text/csv;charset=UTF-8");
            ext = ".csv";
        } else if ("pdf".equalsIgnoreCase(format)) {
            content = exportService.toPdf(title, headers, body);
            type = MediaType.APPLICATION_PDF;
            ext = ".pdf";
        } else {
            content = exportService.toXlsx(title, headers, body);
            type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            ext = ".xlsx";
        }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(fileBase + ext).build().toString())
                .body(content);
    }

    /** Comma separated, every value in quotes, with a byte-order mark so Excel opens the accents right. A value that would be read as a formula is made harmless. */
    public static byte[] csv(List<String> headers, List<List<String>> body) {
        StringBuilder b = new StringBuilder("﻿");
        b.append(line(headers));
        for (List<String> row : body) b.append(line(row));
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String line(List<String> cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) b.append(',');
            String v = cells.get(i) == null ? "" : cells.get(i);
            if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("-?[0-9][0-9.,]*")) v = "'" + v;
            b.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        return b.append("\r\n").toString();
    }
}
