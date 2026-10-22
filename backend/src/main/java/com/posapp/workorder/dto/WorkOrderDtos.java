package com.posapp.workorder.dto;

import com.posapp.workorder.WorkOrderPriority;
import com.posapp.workorder.WorkOrderStatus;
import com.posapp.workorder.WorkOrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkOrderDtos {

    private WorkOrderDtos() {
    }

    /** One row of the work order lists. */
    public record WorkOrderSummary(Long id, String number, WorkOrderType type, WorkOrderStatus status, WorkOrderPriority priority, String title,
                                   Long customerId, String customerName, Long assetId, String assetName, String packageName,
                                   Long assignedToUserId, String assignedToName, LocalDate scheduledDate, LocalDate dueDate, boolean overdue,
                                   int checklistDone, int checklistTotal, BigDecimal hours, Long invoiceId, String invoiceNumber, boolean readyToInvoice,
                                   Instant createdAt) {
    }

    public record LineDto(Long id, Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal unitCost, boolean included,
                          BigDecimal lineTotal) {
    }

    public record ChecklistDto(Long id, int seq, String text, boolean done, Instant doneAt, String doneByName) {
    }

    public record CostDto(Long id, LocalDate costDate, String description, String supplierName, BigDecimal amount, BigDecimal chargeAmount, Long expenseId) {
    }

    /** Numbers shown on the job: what it cost and what it earns. */
    public record Money(BigDecimal partsCost, BigDecimal externalCost, BigDecimal totalCost, BigDecimal partsCharged, BigDecimal costsCharged,
                        BigDecimal labourBillable, BigDecimal expectedRevenue, BigDecimal invoiceTotal) {
    }

    public record WorkOrderDetail(Long id, String number, WorkOrderType type, WorkOrderStatus status, WorkOrderPriority priority, String title,
                                  String description, String itemDescription, Long customerId, String customerName, Long assetId, String assetName,
                                  Long packageId, String packageName, BigDecimal fixedPrice, BigDecimal labourRate, Long assignedToUserId,
                                  String assignedToName, LocalDate requestedDate, LocalDate scheduledDate, LocalDate dueDate, Instant startedAt,
                                  Instant completedAt, Long timeProjectId, BigDecimal hours, BigDecimal billableHours, Long invoiceId, String invoiceNumber,
                                  Long manufacturingOrderId, String manufacturingOrderNumber, String manufacturingOrderStatus, Long branchId,
                                  boolean partsPosted, String completionNote, String createdByName, Instant createdAt, boolean overdue,
                                  boolean canWork, boolean canManage, boolean readyToInvoice, List<LineDto> lines, List<ChecklistDto> checklist,
                                  List<CostDto> costs, Money money) {
    }

    public record SummaryDto(long open, long inProgress, long onHold, long overdue, long readyToInvoice, long mine) {
    }

    // ---- service packages

    public record PackageItemDto(Long id, String text) {
    }

    public record PackagePartDto(Long id, Long productId, String productName, int quantity, boolean included) {
    }

    public record PackageSummary(Long id, String code, String name, BigDecimal fixedPrice, BigDecimal estimatedHours, boolean active, int steps, int parts,
                                 long timesUsed) {
    }

    public record PackageDetail(Long id, String code, String name, String description, BigDecimal fixedPrice, BigDecimal estimatedHours, boolean active,
                                List<PackageItemDto> items, List<PackagePartDto> parts) {
    }

    // ---- billing

    public record InvoiceLinePreview(String kind, String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record InvoicePreview(Long workOrderId, String number, Long customerId, String customerName, List<InvoiceLinePreview> lines, BigDecimal total,
                                 int timeEntries, String warning) {
    }

    public record InvoiceResult(Long invoiceId, String invoiceNumber, BigDecimal total) {
    }
}
