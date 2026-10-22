package com.posapp.law;

import com.posapp.law.dto.LawCalendarDtos.CalendarEntry;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Builds an iCalendar (.ics) file from calendar entries. Opens in Outlook, Google Calendar, Apple Calendar and on phones. */
final class LawIcal {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private LawIcal() {
    }

    static byte[] build(String calendarName, List<CalendarEntry> entries, LocalDateTime now) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//NIWE//Law Firm//EN");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + esc(calendarName));
        String stamp = now.atOffset(ZoneOffset.UTC).format(STAMP) + "Z";
        for (CalendarEntry e : entries) {
            boolean task = "TASK".equals(e.kind());
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:law-" + e.kind().toLowerCase() + "-" + e.id() + "@niwe-pos");
            line(sb, "DTSTAMP:" + stamp);
            if (e.time() != null) {
                LocalDateTime start = LocalDateTime.of(e.date(), e.time());
                line(sb, "DTSTART:" + start.format(STAMP));
                line(sb, "DTEND:" + start.plusHours(1).format(STAMP));
            } else {
                LocalDate d = e.date();
                line(sb, "DTSTART;VALUE=DATE:" + d.format(DAY));
                line(sb, "DTEND;VALUE=DATE:" + d.plusDays(1).format(DAY));
            }
            line(sb, "SUMMARY:" + esc((task ? "Task: " : "") + e.title() + (e.matterNumber() != null ? " (" + e.matterNumber() + ")" : "")));
            StringBuilder desc = new StringBuilder();
            if (e.matterNumber() != null) desc.append("Matter: ").append(e.matterNumber()).append(' ').append(e.matterTitle() == null ? "" : e.matterTitle());
            if (e.assignedToName() != null) desc.append("\nAssigned to: ").append(e.assignedToName());
            if (e.type() != null) desc.append("\n").append(task ? "Priority: " : "Type: ").append(e.type());
            line(sb, "DESCRIPTION:" + esc(desc.toString()));
            if (!task && e.type() != null) line(sb, "CATEGORIES:" + esc(e.type()));
            line(sb, "STATUS:" + (e.done() ? "CANCELLED" : "CONFIRMED"));
            if (!e.done()) {
                line(sb, "BEGIN:VALARM");
                line(sb, "ACTION:DISPLAY");
                line(sb, "DESCRIPTION:" + esc(e.title()));
                line(sb, "TRIGGER:-P1D");
                line(sb, "END:VALARM");
            }
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }

    /** One content line, folded so that no physical line is longer than 75 bytes; never splits a multi-byte character. */
    private static void line(StringBuilder out, String text) {
        int bytes = 0;
        int limit = 75;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int n = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + n > limit) {
                out.append("\r\n ");
                bytes = 1;
                limit = 75;
            }
            out.appendCodePoint(cp);
            bytes += n;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }
}
