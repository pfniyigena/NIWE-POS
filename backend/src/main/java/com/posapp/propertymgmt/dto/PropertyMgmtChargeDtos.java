package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.PmChargeKind;
import com.posapp.propertymgmt.PmChargeSource;
import com.posapp.propertymgmt.PmExtraStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the utilities and service charges screens read and send. */
public final class PropertyMgmtChargeDtos {

    private PropertyMgmtChargeDtos() {
    }

    public record ChargeTypeDto(Long id, String name, PmChargeKind kind, String unitLabel, BigDecimal defaultAmount, boolean active, long leaseCharges, boolean inUse) {
    }

    public record ChargeTypeRequest(String name, PmChargeKind kind, String unitLabel, BigDecimal defaultAmount, Boolean active, Boolean clearDefaultAmount) {
    }

    /** A charge a lease carries. lastReading / lastReadingOn: the latest meter reading (or the opening one), for metered charges. */
    public record LeaseChargeDto(Long id, Long chargeTypeId, String typeName, PmChargeKind kind, String unitLabel, BigDecimal amount, LocalDate startDate, LocalDate endDate, BigDecimal openingReading,
                                 BigDecimal lastReading, LocalDate lastReadingOn, boolean inForce, boolean canDelete, String note) {
    }

    public record LeaseChargeRequest(Long chargeTypeId, BigDecimal amount, LocalDate startDate, LocalDate endDate, Boolean clearEndDate, BigDecimal openingReading, String note) {
    }

    public record ReadingRequest(LocalDate date, BigDecimal reading, String note) {
    }

    public record ExtraRequest(String description, Long chargeTypeId, BigDecimal amount, LocalDate date, String note) {
    }

    /** A meter reading or a one-off charge. */
    public record ExtraDto(Long id, PmChargeSource kind, Long leaseChargeId, Long chargeTypeId, String typeName, LocalDate date, String description, BigDecimal quantity, String unitLabel, BigDecimal rate,
                           BigDecimal amount, BigDecimal readingFrom, BigDecimal readingTo, PmExtraStatus status, String invoiceNumber, String note, String createdByName, boolean canCancel) {
    }

    /** One line a rent invoice has taken (or would take). */
    public record BilledDto(Long id, Long rentChargeId, LocalDate periodStart, LocalDate periodEnd, String invoiceNumber, String name, String description, PmChargeSource source, BigDecimal amount) {
    }

    /** What the next rent invoice of the lease will carry: the rent, and each charge on top. */
    public record PreviewLine(PmChargeSource source, String name, String description, BigDecimal amount) {
    }

    public record InvoicePreview(Long rentChargeId, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal rent, List<PreviewLine> lines, BigDecimal extras, BigDecimal total) {
    }

    public record LeaseChargesView(Long leaseId, String leaseCode, String leaseStatus, String tenantName, String unitLabel, boolean canManage, boolean canAddReadings, List<LeaseChargeDto> charges,
                                   List<ExtraDto> pending, List<ExtraDto> history, List<BilledDto> billed, InvoicePreview next, List<ChargeTypeDto> types) {
    }

    /** One row of the report of charges taken by rent invoices. */
    public record ChargeReportRow(LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, String propertyName, String unitNo, String ownerName, String tenantName, String leaseCode,
                                  String name, PmChargeSource source, String description, String invoiceNumber, BigDecimal amount) {
    }
}
