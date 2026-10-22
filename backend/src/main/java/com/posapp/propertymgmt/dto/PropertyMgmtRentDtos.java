package com.posapp.propertymgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the rent screens read: the rent charges of the leases, the invoices they became, and who is behind. */
public final class PropertyMgmtRentDtos {

    private PropertyMgmtRentDtos() {
    }

    /**
     * One period's rent. status: NOT_INVOICED, WAIVED, UNPAID (invoiced, not due yet, nothing paid), PARTLY_PAID, PAID or OVERDUE (something is owed past the due date).
     * readyToInvoice says a not-yet-invoiced charge is within the invoicing window. afterLeaving: the lease was terminated and this period starts after the tenant left.
     */
    public record RentChargeRow(Long id, Long leaseId, String leaseCode, Long unitId, String unitNo, Long propertyId, String propertyName, Long ownerId, String ownerName, Long tenantId,
                                String tenantName, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal amount, boolean prorated, String status, Long invoiceId,
                                String invoiceNumber, BigDecimal billed, BigDecimal paid, BigDecimal outstanding, Long daysOverdue, String waivedReason, boolean readyToInvoice,
                                boolean afterLeaving, int reminders, LocalDate lastReminderOn, BigDecimal lateFee, String lateFeeInvoice, BigDecimal extras) {
    }

    public record RentSchedule(Long leaseId, String leaseCode, List<RentChargeRow> charges, BigDecimal billed, BigDecimal paid, BigDecimal outstanding, BigDecimal overdue,
                               BigDecimal notYetInvoiced, long afterLeaving, boolean canManage) {
    }

    public record InvoiceRun(int invoiced, int failed, BigDecimal amount, List<String> problems) {
    }

    /** One tenant's overdue rent, with its ageing: 1 to 30, 31 to 60, 61 to 90 and over 90 days past due. */
    public record ArrearsRow(Long tenantId, String tenantCode, String tenantName, String phone, long charges, BigDecimal outstanding, BigDecimal days30, BigDecimal days60,
                             BigDecimal days90, BigDecimal days90Plus, LocalDate oldestDue, Long oldestDays, List<RentChargeRow> items) {
    }

    public record Arrears(BigDecimal total, BigDecimal days30, BigDecimal days60, BigDecimal days90, BigDecimal days90Plus, long tenants, List<ArrearsRow> rows) {
    }

    /** Rent by the month it fell due. */
    public record CollectionRow(String month, long charges, BigDecimal invoiced, BigDecimal collected, BigDecimal outstanding, BigDecimal notYetInvoiced, BigDecimal collectedPercent) {
    }

    public record RentSummary(long toInvoice, BigDecimal toInvoiceAmount, BigDecimal arrears, long tenantsInArrears) {
    }
}
