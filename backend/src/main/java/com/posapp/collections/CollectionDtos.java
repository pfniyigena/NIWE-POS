package com.posapp.collections;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Customer collections: what customers owe and how late it is, statements and reminder e-mails. */
public final class CollectionDtos {

    private CollectionDtos() {
    }

    /** What is owed, by how long it is past due. */
    public record Aging(BigDecimal current, BigDecimal days1to30, BigDecimal days31to60, BigDecimal days61to90, BigDecimal over90) {
    }

    /** One unpaid sale or invoice. {@code kind}: INVOICE or POS_SALE (a till sale on credit). {@code dueAssumed}: no due date was set, so one was worked out from the credit term. */
    public record ItemRow(String kind, Long documentId, String number, Long customerId, String customerName, String customerEmail, String customerPhone, LocalDate date, LocalDate dueDate,
                          boolean dueAssumed, BigDecimal total, BigDecimal paid, BigDecimal outstanding, long daysOverdue, String bucket, Long branchId) {
    }

    public record Summary(BigDecimal totalOutstanding, BigDecimal totalOverdue, int overdueItems, int customersOwing, int customersOverdue, Aging aging) {
    }

    /** The items screen: one page of rows, how many matched, and the totals of everything that matched the customer / branch filters. */
    public record ItemsPage(List<ItemRow> rows, int total, Summary summary, boolean canRemind, boolean mailReady, int defaultTermDays) {
    }

    /** One customer who owes. */
    public record CustomerRow(Long customerId, String name, String email, String phone, int items, int overdueItems, BigDecimal outstanding, BigDecimal overdue, long oldestDaysOverdue,
                              Aging aging, int remindersSent, Instant lastRemindedAt, boolean creditEnabled) {
    }

    public record CustomersPage(List<CustomerRow> rows, int total, Summary summary, boolean canRemind, boolean mailReady) {
    }

    /** A line on a customer statement. */
    public record StatementLine(LocalDate date, String number, String kind, BigDecimal total, BigDecimal paid, BigDecimal outstanding, LocalDate dueDate, long daysOverdue) {
    }

    public record PaymentLine(LocalDate date, String receipt, String method, BigDecimal amount) {
    }

    public record StatementData(Long customerId, String customerName, String email, String phone, LocalDate asOf, List<StatementLine> lines, List<PaymentLine> payments, BigDecimal totalOutstanding,
                                BigDecimal totalOverdue, Aging aging) {
    }

    public record RemindRequest(List<Long> customerIds) {
    }

    /** A customer who has been (or would be) reminded. {@code problem}: why nobody could be mailed. */
    public record ReminderItem(Long customerId, String customerName, String email, int items, BigDecimal outstanding, String problem) {
    }

    public record RemindResult(int mails, int items, int skipped, List<ReminderItem> customers) {
    }

    public record MailedStatement(String email, String message) {
    }

    public record Printed(byte[] content, String fileName) {
    }

    public record SettingsDto(boolean autoEnabled, int firstDays, int repeatDays, int maxReminders, int defaultTermDays, boolean mailReady) {
    }

    public record SettingsRequest(Boolean autoEnabled, Integer firstDays, Integer repeatDays, Integer maxReminders, Integer defaultTermDays) {
    }

    public record LogRow(Long id, Long customerId, String customerName, String kind, String email, int items, BigDecimal amount, String sentBy, Instant sentAt) {
    }
}
