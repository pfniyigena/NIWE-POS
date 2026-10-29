package com.posapp.booking;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** A one-event calendar file (.ics) for an appointment, so a guest can add it to their phone or e-mail calendar. */
public final class BookingIcs {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private BookingIcs() {
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    /** Lines longer than 75 characters are folded, as the format requires. */
    private static void line(StringBuilder sb, String text) {
        String t = text;
        while (t.length() > 74) {
            sb.append(t, 0, 74).append("\r\n ");
            t = t.substring(74);
        }
        sb.append(t).append("\r\n");
    }

    public static String filename(Appointment a) {
        return a.getNumber().replaceAll("[^A-Za-z0-9_-]", "") + ".ics";
    }

    public static byte[] build(Appointment a, ZoneId zone, String businessName) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//NIWE ERP//Booking//EN");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "BEGIN:VEVENT");
        line(sb, "UID:" + a.getConfirmationCode() + "@niwe-booking");
        line(sb, "DTSTAMP:" + java.time.Instant.now().atZone(ZoneOffset.UTC).format(UTC));
        line(sb, "DTSTART:" + a.getStartAt().atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(UTC));
        line(sb, "DTEND:" + a.getEndAt().atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(UTC));
        line(sb, "SUMMARY:" + esc(a.getServiceName() + (businessName != null && !businessName.isBlank() ? " - " + businessName : "")));
        StringBuilder desc = new StringBuilder("Booking code " + a.getConfirmationCode());
        if (a.getStaffName() != null) desc.append("\nWith ").append(a.getStaffName());
        if (a.getResourceName() != null) desc.append("\n").append(a.getResourceName());
        line(sb, "DESCRIPTION:" + esc(desc.toString()));
        if (businessName != null && !businessName.isBlank()) line(sb, "LOCATION:" + esc(businessName));
        line(sb, "STATUS:" + (a.getStatus() == AppointmentStatus.CANCELLED ? "CANCELLED" : "CONFIRMED"));
        line(sb, "END:VEVENT");
        line(sb, "END:VCALENDAR");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
