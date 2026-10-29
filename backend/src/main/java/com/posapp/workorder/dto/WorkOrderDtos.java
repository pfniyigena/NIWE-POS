package com.posapp.workorder.dto;

import com.posapp.workorder.ApprovalStatus;
import com.posapp.workorder.IntervalUnit;
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
                                   Instant createdAt, boolean approvalRequired, ApprovalStatus approvalStatus, Long scheduleId) {
    }

    public record LineDto(Long id, Long productId, String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitCost, boolean included,
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
                                  List<CostDto> costs, Money money, boolean approvalRequired, ApprovalStatus approvalStatus, BigDecimal estimatedHours, Long scheduleId,
                                  Instant completionEmailedAt) {
    }

    public record SummaryDto(long open, long inProgress, long onHold, long overdue, long readyToInvoice, long mine) {
    }

    // ---- service packages

    public record PackageItemDto(Long id, String text) {
    }

    public record PackagePartDto(Long id, Long productId, String productName, BigDecimal quantity, boolean included) {
    }

    public record PackageSummary(Long id, String code, String name, BigDecimal fixedPrice, BigDecimal estimatedHours, boolean active, int steps, int parts,
                                 long timesUsed) {
    }

    public record PackageDetail(Long id, String code, String name, String description, BigDecimal fixedPrice, BigDecimal estimatedHours, boolean active,
                                List<PackageItemDto> items, List<PackagePartDto> parts) {
    }

    // ---- billing

    public record InvoiceLinePreview(String kind, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record InvoicePreview(Long workOrderId, String number, Long customerId, String customerName, List<InvoiceLinePreview> lines, BigDecimal total,
                                 int timeEntries, String warning) {
    }

    public record InvoiceResult(Long invoiceId, String invoiceNumber, BigDecimal total) {
    }

    // ---- v1.15.0: estimate and approval, notes, files, history, settings, schedules
    public record EstimateLine(String kind, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record EstimateDto(Long workOrderId, String number, boolean approvalRequired, ApprovalStatus status, BigDecimal estimatedHours, BigDecimal labourRate,
                              BigDecimal fixedPrice, List<EstimateLine> lines, BigDecimal total, BigDecimal totalShownToCustomer, Instant requestedAt,
                              Instant decidedAt, String decidedByName, String note, String recordedBy, boolean changedSinceRequest, boolean canStart,
                              boolean emailSent, String emailNote) {
    }

    public record NoteDto(Long id, Long authorUserId, String authorName, String text, Instant createdAt, boolean canDelete) {
    }

    public record AttachmentDto(Long id, String fileName, String contentType, long sizeBytes, String caption, String uploadedByName, Instant createdAt,
                                boolean image, boolean canDelete) {
    }

    public record HistoryDto(Instant at, String who, String action, String description) {
    }

    public record SettingsDto(boolean requireApprovalDefault, boolean emailCustomerOnComplete) {
    }

    public record ScheduleDto(Long id, String title, String description, String itemDescription, WorkOrderType type, WorkOrderPriority priority,
                              Long customerId, String customerName, Long assetId, String assetName, Long packageId, String packageName,
                              Long assignedToUserId, String assignedToName, BigDecimal fixedPrice, BigDecimal labourRate, Long branchId,
                              IntervalUnit intervalUnit, int intervalCount, LocalDate nextDueDate, int leadDays, int dueAfterDays, boolean skipIfOpen,
                              boolean active, Instant lastGeneratedAt, Long lastWorkOrderId, String lastWorkOrderNumber, String lastWorkOrderStatus,
                              int timesGenerated, boolean dueNow, String every) {
    }

    public record ScheduleRunResult(int created, int skipped, List<String> numbers, List<String> notes) {
    }
}
