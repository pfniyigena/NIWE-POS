package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rows of the second set of law firm reports (productivity, profitability, ageing, documents and portal, conflict log, caseload). */
public final class LawInsightRows {

    private LawInsightRows() {
    }

    /** What one lawyer logged in a period. Rejected time is left out. Values are hours x the rate on each entry. */
    public record ProductivityRow(String lawyer, int matters, BigDecimal hours, BigDecimal billableHours, BigDecimal nonBillableHours, BigDecimal billablePercent,
                                  BigDecimal billableValue, BigDecimal billedValue, BigDecimal notYetBilledValue) {
    }

    /**
     * One matter's result. Fees billed less the cost of the time put in (hours x each person's cost per hour), plus what was recovered from clients for costs
     * less what those costs were. {@code profit} is empty when none of the matter's hours has a cost rate, and {@code hoursWithoutCostRate} says how many hours
     * were counted at no cost because their person has no rate set.
     */
    public record ProfitRow(String number, String title, String client, String billing, String status, LocalDate opened, long daysOpen, BigDecimal hours, BigDecimal timeValue,
                            BigDecimal feesBilled, BigDecimal billed, BigDecimal paid, BigDecimal outstanding, BigDecimal collectedPercent, BigDecimal timeCost,
                            BigDecimal costsPaidOut, BigDecimal costsRecovered, BigDecimal profit, BigDecimal profitPercent, BigDecimal hoursWithoutCostRate,
                            BigDecimal notYetBilled) {
    }

    /** One unpaid invoice of a matter, in the age band it falls in. */
    public record AgeingRow(String matter, String title, String client, String invoice, LocalDate invoiceDate, long days, BigDecimal billed, BigDecimal outstanding,
                            BigDecimal band0to30, BigDecimal band31to60, BigDecimal band61to90, BigDecimal bandOver90) {
    }

    public record DocumentsPortalRow(String number, String title, String client, String status, int documents, BigDecimal storageMb, int confidential, int sharedWithClient,
                                     int waitingForReview, String portal, int clientPeople, String lastClientSignIn, int unansweredMessages) {
    }

    public record ConflictLogRow(String when, String checkedBy, String searched, String matter, int hits, String result) {
    }

    public record CaseloadRow(String practiceArea, String court, int opened, int closed, int openNow, int onHold, BigDecimal averageDaysToClose) {
    }
}
