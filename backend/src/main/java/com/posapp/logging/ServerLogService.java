package com.posapp.logging;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * v1.14.0: lets an administrator read the server's own log files (see logback-spring.xml) from the app. Only {@code *.log} files directly inside
 * {@code app.logging.dir} can be named, nothing else on the disk can be reached.
 */
@Service
public class ServerLogService {
    public record LogFile(String name, long size, Instant modified) {}
    public record Entry(String time, String level, String text) {}
    public record Tail(String file, long size, Instant modified, boolean truncated, int matched, List<Entry> entries) {}

    private static final Pattern NAME = Pattern.compile("niwe-[A-Za-z0-9._-]{1,110}\\.log");
    private static final Pattern START = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)\\s+([A-Z]{4,5})\\b.*");
    private static final List<String> ORDER = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    private static final long MAX_READ = 4L * 1024 * 1024;

    private final Path dir;
    private final AuditLogService auditLogService;

    public ServerLogService(@Value("${app.logging.dir:logs}") String dir, AuditLogService auditLogService) {
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
        this.auditLogService = auditLogService;
    }

    public List<LogFile> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).filter(p -> NAME.matcher(p.getFileName().toString()).matches())
                    .map(p -> {
                        try {
                            return new LogFile(p.getFileName().toString(), Files.size(p), Files.getLastModifiedTime(p).toInstant());
                        } catch (IOException e) {
                            return null;
                        }
                    }).filter(x -> x != null)
                    .sorted(Comparator.comparing(LogFile::modified).reversed()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The path of a listed log file, or a 404; the name is never used to build a path unless it is exactly one of the listed names. */
    public Path resolve(String name) {
        if (name == null || !NAME.matcher(name).matches() || list().stream().noneMatch(f -> f.name().equals(name))) {
            throw ApiException.notFound("No such log file");
        }
        return dir.resolve(name);
    }

    public Tail tail(String name, int lines, String minLevel, String text) {
        Path p = resolve(name);
        int max = Math.max(10, Math.min(lines, 2000));
        String lvl = minLevel == null ? "" : minLevel.trim().toUpperCase(Locale.ROOT);
        int minIdx = ORDER.indexOf(lvl);
        String needle = text == null || text.isBlank() ? null : text.trim().toLowerCase(Locale.ROOT);
        try (RandomAccessFile raf = new RandomAccessFile(p.toFile(), "r")) {
            long size = raf.length();
            long start = Math.max(0, size - MAX_READ);
            boolean truncated = start > 0;
            raf.seek(start);
            byte[] buf = new byte[(int) (size - start)];
            raf.readFully(buf);
            String all = new String(buf, StandardCharsets.UTF_8);
            if (truncated) {
                int nl = all.indexOf('\n');
                all = nl < 0 ? "" : all.substring(nl + 1);
            }
            List<Entry> entries = new ArrayList<>();
            String time = null, level = null;
            StringBuilder cur = null;
            for (String line : all.split("\r?\n", -1)) {
                var m = START.matcher(line);
                if (m.matches() && ORDER.contains(m.group(2))) {
                    if (cur != null) entries.add(new Entry(time, level, cur.toString()));
                    time = m.group(1);
                    level = m.group(2);
                    cur = new StringBuilder(line);
                } else if (cur != null) {
                    if (!line.isEmpty()) cur.append('\n').append(line);
                } else if (!line.isEmpty()) {
                    cur = new StringBuilder(line);
                }
            }
            if (cur != null) entries.add(new Entry(time, level, cur.toString()));
            List<Entry> kept = new ArrayList<>();
            for (Entry e : entries) {
                if (minIdx > 0 && (e.level() == null || ORDER.indexOf(e.level()) < minIdx)) continue;
                if (needle != null && !e.text().toLowerCase(Locale.ROOT).contains(needle)) continue;
                kept.add(e);
            }
            int matched = kept.size();
            List<Entry> out = matched > max ? kept.subList(matched - max, matched) : kept;
            return new Tail(name, size, Files.getLastModifiedTime(p).toInstant(), truncated, matched, new ArrayList<>(out));
        } catch (IOException e) {
            throw ApiException.badRequest("The log file could not be read: " + e.getMessage());
        }
    }

    public void auditDownload(String name) {
        auditLogService.record("SERVER_LOG_DOWNLOADED", "Downloaded the server log " + name);
    }
}
