package com.posapp.law.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Fee collections: unpaid invoices with their age, the client fee statement and the reminder e-mails. */
public final class LawCollectionDtos {

    private LawCollectionDtos() {
    }

    /** What is owed, by how long it is past due. */
    public record Aging(BigDecimal current, BigDecimal days1to30, BigDecimal days31to60, BigDecimal days61to90, BigDecimal over90) {
    }

    /** One unpaid invoice. {@code dueAssumed}: the invoice has no due date, so one was worked out from the credit term. */
    public record CollectionRow(Long billId, Long invoiceId, String invoiceNumber, Long matterId, String matterNumber, String matterTitle, Long clientId, String clientName,
                                String clientEmail, LocalDate invoiceDate, LocalDate dueDate, boolean dueAssumed, BigDecimal amount, BigDecimal paid, BigDecimal outstanding,
                                long daysOverdue, int reminderCount, Instant lastRemindedAt) {
    }

    public record CollectionsDto(List<CollectionRow> rows, BigDecimal totalOutstanding, BigDecimal totalOverdue, int overdueInvoices, int clientsOwing, Aging aging,
                                 boolean canRemind, boolean mailReady, int defaultTermDays) {
    }

    /** One invoice on a client's statement. */
    public record FeeLine(LocalDate date, String invoiceNumber, String matter, BigDecimal amount, BigDecimal paid, BigDecimal outstanding, LocalDate dueDate, long daysOverdue) {
    }

    public record FeeStatementData(String clientName, String clientEmail, String clientPhone, LocalDate asOf, LocalDate from, LocalDate to, List<FeeLine> lines,
                                   BigDecimal totalBilled, BigDecimal totalPaid, BigDecimal totalOutstanding, Aging aging) {
    }

    public record RemindRequest(List<Long> billIds) {
    }

    /** A client who has been (or would be) reminded. {@code problem}: why nobody can be mailed. */
    public record ReminderItem(Long clientId, String clientName, String email, int invoices, BigDecimal outstanding, String problem) {
    }

    public record RemindResult(int mails, int invoices, int skipped, List<ReminderItem> items) {
    }
}
