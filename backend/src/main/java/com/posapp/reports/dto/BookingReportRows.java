package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rows of the booking reports. */
public final class BookingReportRows {

    private BookingReportRows() {
    }

    public record AppointmentRow(LocalDate date, String time, String number, String service, String customer, String staff, String resource, String status,
                                 BigDecimal price, BigDecimal paid, String invoice) {
    }

    public record UtilisationRow(String type, String name, int appointments, BigDecimal bookedHours, BigDecimal availableHours, BigDecimal utilisationPercent) {
    }

    public record ServiceRow(String service, int booked, int completed, int cancelled, int noShows, BigDecimal noShowPercent, BigDecimal value) {
    }
}
