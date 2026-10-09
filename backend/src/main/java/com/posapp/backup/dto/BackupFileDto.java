package com.posapp.backup.dto;

import java.time.Instant;

/** One backup file sitting in {@code app.backup.directory} — see BackupService's own javadoc. */
public record BackupFileDto(String filename, long sizeBytes, Instant createdAt) {
}
