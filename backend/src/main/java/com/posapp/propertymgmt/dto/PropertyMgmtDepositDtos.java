package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.PmDepositKind;
import com.posapp.propertymgmt.PmDepositReason;
import com.posapp.propertymgmt.PmInspectionKind;
import com.posapp.propertymgmt.PmInspectionStatus;
import com.posapp.propertymgmt.PmItemCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the security deposit and inspection screens read and send. */
public final class PropertyMgmtDepositDtos {

    private PropertyMgmtDepositDtos() {
    }

    public record TxnDto(Long id, PmDepositKind kind, BigDecimal amount, LocalDate date, String method, PmDepositReason reason, String note, Long invoiceId, String invoiceNumber, String receiptNumber,
                         Long otherLeaseId, String otherLeaseCode, boolean voided, String voidReason, Instant voidedAt, String recordedByName, boolean canVoid) {
    }

    /** A rent invoice of the lease that is still owed, which a deduction can settle. */
    public record OpenInvoice(Long invoiceId, String invoiceNumber, LocalDate dueDate, BigDecimal outstanding) {
    }

    /** A lease of the same tenant the held deposit can be moved to (a renewal). */
    public record TransferTarget(Long leaseId, String leaseCode, String status, BigDecimal agreed, BigDecimal paidIn, BigDecimal room) {
    }

    /**
     * One lease's deposit. state: NONE (no deposit agreed), DUE (agreed, nothing received), PART_PAID, HELD, REFUND_DUE (the lease is over and money is still held), SETTLED (all of it
     * paid out or kept).
     */
    public record DepositView(Long leaseId, String leaseCode, String leaseStatus, Long tenantId, String tenantName, String propertyName, String unitNo, BigDecimal agreed, BigDecimal received,
                              BigDecimal transferredIn, BigDecimal deducted, BigDecimal refunded, BigDecimal transferredOut, BigDecimal paidIn, BigDecimal held, BigDecimal stillToPay, String state,
                              boolean canReceive, boolean canSettle, boolean canTransfer, boolean canManage, BigDecimal suggestedDamages, Long moveOutInspectionId, List<TxnDto> txns,
                              List<OpenInvoice> openInvoices, List<TransferTarget> transferTargets) {
    }

    /** A row of the deposits list. */
    public record DepositRow(Long leaseId, String leaseCode, String leaseStatus, String leaseState, Long tenantId, String tenantName, Long propertyId, String propertyName, String unitNo,
                             Long ownerId, String ownerName, LocalDate endDate, Long daysToEnd, BigDecimal agreed, BigDecimal paidIn, BigDecimal deducted, BigDecimal refunded, BigDecimal held,
                             String state) {
    }

    public record DepositTotals(BigDecimal held, BigDecimal toCollect, BigDecimal refundDue, long refundDueCount, long heldCount) {
    }

    public record ReceiveRequest(BigDecimal amount, LocalDate date, String method, String note) {
    }

    public record DeductRequest(BigDecimal amount, LocalDate date, PmDepositReason reason, Long invoiceId, String note) {
    }

    public record RefundRequest(BigDecimal amount, LocalDate date, String method, String note) {
    }

    public record TransferRequest(Long toLeaseId, BigDecimal amount, LocalDate date, String note) {
    }

    public record VoidRequest(String reason) {
    }

    // ------------------------------------------------------------------ inspections

    public record ItemDto(Long id, int sortOrder, String room, String item, PmItemCondition condition, PmItemCondition baseline, boolean worse, String notes, BigDecimal repairCost) {
    }

    public record InspectionRow(Long id, String code, Long leaseId, String leaseCode, String tenantName, String propertyName, String unitNo, PmInspectionKind kind, PmInspectionStatus status,
                                LocalDate date, String inspectorName, int items, int assessed, int problems, BigDecimal repairCost, boolean tenantAcknowledged) {
    }

    /** A whole inspection. problems = items that are POOR, DAMAGED or MISSING; worse = items worse than at move-in. */
    public record InspectionDto(Long id, String code, Long leaseId, String leaseCode, Long tenantId, String tenantName, Long propertyId, String propertyName, Long unitId, String unitNo,
                                PmInspectionKind kind, PmInspectionStatus status, LocalDate date, String inspectorName, String electricityReading, String waterReading, Integer keysHanded,
                                String notes, boolean tenantAcknowledged, String tenantSignedName, LocalDate signedOffOn, String signedOffByName, String createdByName, List<ItemDto> items,
                                int assessed, int problems, int worse, BigDecimal repairCost, Long moveInInspectionId, boolean canManage) {
    }

    /** Starts an inspection. kind is needed. copyFromMoveIn (move-out): start from the move-in list, with its conditions as the comparison. rooms (optional) = the rooms to start the list with. */
    public record InspectionRequest(PmInspectionKind kind, LocalDate date, String inspectorName, Boolean copyFromMoveIn, List<String> rooms) {
    }

    /** Edits the top of an inspection: anything left out stays as it was; a blank text clears it. */
    public record InspectionUpdate(LocalDate date, String inspectorName, String electricityReading, String waterReading, Integer keysHanded, String notes) {
    }

    /** Edits one item: anything left out stays as it was; a blank text clears it. clearCondition puts it back to not looked at; clearCost removes the cost. */
    public record ItemRequest(String room, String item, PmItemCondition condition, Boolean clearCondition, String notes, BigDecimal repairCost, Boolean clearCost) {
    }

    public record SignOffRequest(Boolean tenantAcknowledged, String tenantSignedName) {
    }
}
