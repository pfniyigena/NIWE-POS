package com.posapp.backup;

import com.posapp.audit.AuditLogService;
import com.posapp.backup.dto.BackupFileDto;
import com.posapp.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fourier: "there's no backup/restore ... anywhere in the backend ... what if the server dies."
 * Shells out to the {@code pg_dump}/{@code psql} command-line tools that ship with PostgreSQL
 * (there's no pure-Java equivalent worth depending on for this) rather than anything JDBC-level,
 * since a full logical dump/restore of the database is exactly what those tools are for — same
 * "use the real database tool for a database-shaped problem" reasoning the Reports module applies
 * to Excel/PDF via POI/OpenPDF rather than hand-rolling either format.
 * <p>
 * Every dump is plain SQL (not pg_dump's custom {@code -F c} format) with {@code --clean
 * --if-exists}, so the resulting file is both human-inspectable and directly replayable with
 * {@code psql -f} — no separate {@code pg_restore} step, and no ambiguity about which tool
 * restores which format. {@code app.backup.pg-dump-path}/{@code app.backup.psql-path} exist
 * because a self-hosted Windows deployment may not have either on {@code PATH} — an admin can
 * point these at the full executable path instead of installing anything new.
 * <p>
 * Database host/port/name are parsed out of {@code spring.datasource.url} rather than configured
 * separately, so there's exactly one place (the existing datasource config) that says which
 * database this app talks to; {@code PGPASSWORD} is set as a child-process environment variable
 * for both tools rather than passed on the command line, which would otherwise leak the password
 * into the process list/any process-launch logging.
 * <p>
 * Restoring while the app itself holds an open connection pool to the same database is an
 * inherent sharp edge of doing this without taking the app offline first — {@code --clean
 * --if-exists} makes the dump itself replay cleanly, but a restart right after a restore (see
 * BackupController's own javadoc / the frontend's warning copy) is still the safe way to clear any
 * pooled connections/cached state left over from before the restore.
 * <p>
 * Fourier: "true maintenance-mode lockout" — {@link #beginRestore()} turns on
 * {@link MaintenanceModeService} for the duration of the restore, which (via
 * {@link MaintenanceModeFilter}) gets every other request in the app, reads included, a clean 503
 * instead of risking a half-dropped table. The same flag doubles as the "only one restore at a
 * time" guard: a second restore attempt while one is already running fails fast with a 409 rather
 * than racing another {@code psql} process against the same tables.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** Every stored backup's filename matches this exactly — guards every filename-taking method below against path traversal (e.g. "../../etc/passwd"), since these are always built by createBackup(), never typed in by a caller. */
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^pos-backup-\\d{8}-\\d{6}\\.sql$");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)");
    private static final long PROCESS_TIMEOUT_MINUTES = 5;

    private final AuditLogService auditLogService;
    private final MaintenanceModeService maintenanceModeService;

    @Value("${app.backup.directory}")
    private String backupDirectory;

    @Value("${app.backup.pg-dump-path}")
    private String pgDumpPath;

    @Value("${app.backup.psql-path}")
    private String psqlPath;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    public BackupService(AuditLogService auditLogService, MaintenanceModeService maintenanceModeService) {
        this.auditLogService = auditLogService;
        this.maintenanceModeService = maintenanceModeService;
    }

    /** Admin > Data's backup list, newest first — every file in app.backup.directory matching the naming convention this service itself always uses. */
    public List<BackupFileDto> listBackups() {
        Path dir = backupDir();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> FILENAME_PATTERN.matcher(p.getFileName().toString()).matches())
                    .map(this::toDto)
                    .sorted(Comparator.comparing(BackupFileDto::filename).reversed())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list backups in " + dir, e);
        }
    }

    /** "Backup now" — also what the nightly scheduled job below calls. */
    public BackupFileDto createBackup() {
        Path dir = backupDir();
        String filename = "pos-backup-" + TIMESTAMP_FORMAT.format(Instant.now()) + ".sql";
        Path file = dir.resolve(filename);
        DbTarget target = parseDatasourceUrl();

        List<String> command = List.of(pgDumpPath, "--no-owner", "--no-privileges", "--clean", "--if-exists",
                "-h", target.host(), "-p", target.port(), "-U", datasourceUsername, "-f", file.toString(), target.database());
        runProcess(command, "pg_dump");

        BackupFileDto dto = toDto(file);
        auditLogService.record("BACKUP_CREATED", "Created database backup " + filename + " (" + dto.sizeBytes() + " bytes)", "Backup", null);
        return dto;
    }

    /** Fourier: "scheduled DB backup" — nightly, see app.backup.cron. Never lets a failure (pg_dump missing, disk full, DB unreachable) crash the scheduler; next night's run is unaffected. */
    @Scheduled(cron = "${app.backup.cron}")
    public void scheduledBackup() {
        try {
            createBackup();
        } catch (Exception ex) {
            log.error("Scheduled database backup failed: {}", ex.getMessage(), ex);
        }
    }

    /** Streams one stored backup's raw bytes for download — see BackupController. */
    public byte[] readBackup(String filename) {
        Path file = resolveExisting(filename);
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read backup " + filename, e);
        }
    }

    public void deleteBackup(String filename) {
        Path file = resolveExisting(filename);
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete backup " + filename, e);
        }
        auditLogService.record("BACKUP_DELETED", "Deleted database backup " + filename, "Backup", null);
    }

    /** Restores from a backup already sitting in app.backup.directory (e.g. one this same instance made earlier). */
    public void restoreFromStoredBackup(String filename) {
        Path file = resolveExisting(filename);
        beginRestore();
        try {
            restoreFromFile(file);
            auditLogService.record("BACKUP_RESTORED", "Restored database from backup " + filename, "Backup", null);
        } finally {
            maintenanceModeService.deactivate();
        }
    }

    /**
     * Restores from a backup file uploaded through the browser — e.g. one downloaded earlier, or
     * migrated from another machine. Staged to a temp file (never written into app.backup.directory
     * itself, so an upload can't be mistaken for — or collide with — a real timestamped backup)
     * and always cleaned up afterward, success or failure.
     */
    public void restoreFromUploadedFile(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw ApiException.badRequest("Choose a backup file to restore from");
        }
        beginRestore();
        try {
            Path temp;
            try {
                temp = Files.createTempFile("pos-restore-upload-", ".sql");
                upload.transferTo(temp);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to stage the uploaded backup file", e);
            }
            try {
                restoreFromFile(temp);
                auditLogService.record("BACKUP_RESTORED", "Restored database from an uploaded backup file (" + upload.getOriginalFilename() + ")", "Backup", null);
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a temp file — nothing a caller can act on.
                }
            }
        } finally {
            maintenanceModeService.deactivate();
        }
    }

    /** Throws if another restore is already running; otherwise turns maintenance mode on for this one. */
    private void beginRestore() {
        if (!maintenanceModeService.activate()) {
            throw ApiException.conflict("A restore is already in progress — wait for it to finish before starting another.");
        }
    }

    private void restoreFromFile(Path file) {
        DbTarget target = parseDatasourceUrl();
        List<String> command = List.of(psqlPath, "-h", target.host(), "-p", target.port(), "-U", datasourceUsername,
                "-d", target.database(), "-f", file.toString());
        runProcess(command, "psql restore");
    }

    private void runProcess(List<String> command, String label) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("PGPASSWORD", datasourcePassword != null ? datasourcePassword : "");
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw ApiException.badRequest(label + " failed to start — is it installed and on PATH (or app.backup."
                    + (label.startsWith("pg_dump") ? "pg-dump-path" : "psql-path") + " set correctly)? " + e.getMessage());
        }
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw ApiException.badRequest(label + " timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes");
            }
        } catch (IOException e) {
            throw new IllegalStateException(label + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(label + " was interrupted", e);
        }
        if (process.exitValue() != 0) {
            log.warn("{} exited with code {}: {}", label, process.exitValue(), output);
            throw ApiException.badRequest(label + " failed (exit code " + process.exitValue() + "): "
                    + (output.isBlank() ? "no output — check the server log" : output.lines().reduce((a, b) -> b).orElse(output)));
        }
    }

    private Path backupDir() {
        Path dir = Path.of(backupDirectory);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create backup directory " + dir, e);
        }
        return dir;
    }

    private Path resolveExisting(String filename) {
        if (filename == null || !FILENAME_PATTERN.matcher(filename).matches()) {
            throw ApiException.badRequest("Not a valid backup filename: " + filename);
        }
        Path file = backupDir().resolve(filename);
        if (!Files.exists(file)) {
            throw ApiException.notFound("Backup not found: " + filename);
        }
        return file;
    }

    private BackupFileDto toDto(Path file) {
        try {
            long size = Files.size(file);
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            Instant createdAt = attrs.creationTime().toInstant();
            return new BackupFileDto(file.getFileName().toString(), size, createdAt);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read backup file attributes for " + file, e);
        }
    }

    private DbTarget parseDatasourceUrl() {
        Matcher m = JDBC_URL_PATTERN.matcher(datasourceUrl);
        if (!m.matches()) {
            throw new IllegalStateException("Could not parse spring.datasource.url for backup/restore: " + datasourceUrl);
        }
        String host = m.group(1);
        String port = m.group(2) != null ? m.group(2) : "5432";
        String database = m.group(3);
        return new DbTarget(host, port, database);
    }

    private record DbTarget(String host, String port, String database) {
    }
}
