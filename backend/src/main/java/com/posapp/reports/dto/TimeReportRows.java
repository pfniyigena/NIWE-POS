package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rows of the time tracking reports. Hours are decimals. */
public final class TimeReportRows {

    private TimeReportRows() {
    }

    public record TimesheetRow(String person, BigDecimal hours, BigDecimal billableHours, BigDecimal nonBillableHours, BigDecimal billableAmount,
                               BigDecimal pendingHours, BigDecimal invoicedHours) {
    }

    public record ProjectHoursRow(String project, String customer, String status, BigDecimal budgetHours, BigDecimal hoursLogged, BigDecimal billableHours,
                                  BigDecimal percentOfBudget, BigDecimal hoursLeft, BigDecimal billedAmount, BigDecimal unbilledAmount) {
    }

    public record UnbilledRow(String project, String customer, int entries, BigDecimal hours, BigDecimal amount, LocalDate oldest) {
    }

    public record UtilisationRow(String person, BigDecimal capacityHours, BigDecimal loggedHours, BigDecimal billableHours, BigDecimal utilisationPercent,
                                 BigDecimal billablePercent) {
    }

    public record EntryRow(LocalDate date, String person, String project, String customer, String task, String description, BigDecimal hours,
                           String billable, BigDecimal rate, BigDecimal amount, String status) {
    }
}
