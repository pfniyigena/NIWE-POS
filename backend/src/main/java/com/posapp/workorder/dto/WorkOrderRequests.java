package com.posapp.workorder.dto;

import com.posapp.workorder.WorkOrderPriority;
import com.posapp.workorder.WorkOrderType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WorkOrderRequests {

    private WorkOrderRequests() {
    }

    /** Create or edit a work order's header. {@code packageId} is used on create only: it copies the checklist, parts and price. */
    public record WorkOrderRequest(WorkOrderType type, WorkOrderPriority priority, String title, String description, String itemDescription, Long customerId,
                                   Long assetId, Long packageId, BigDecimal fixedPrice, BigDecimal labourRate, Long assignedToUserId, LocalDate requestedDate,
                                   LocalDate scheduledDate, LocalDate dueDate, Long branchId) {
    }

    public record LineRequest(Long productId, BigDecimal quantity, BigDecimal unitPrice, Boolean included) {
    }

    public record LinesRequest(List<LineRequest> lines) {
    }

    public record ChecklistRequest(List<String> items) {
    }

    public record CostRequest(LocalDate costDate, String description, String supplierName, BigDecimal amount, BigDecimal chargeAmount, Long expenseAccountId) {
    }

    public record CompleteRequest(String note, Boolean force) {
    }

    public record ManufacturingRequest(Long bomId, BigDecimal quantity, LocalDate scheduledDate) {
    }

    public record PackageRequest(String code, String name, String description, BigDecimal fixedPrice, BigDecimal estimatedHours, Boolean active,
                                 List<String> items, List<PackagePartRequest> parts) {
    }

    public record PackagePartRequest(Long productId, BigDecimal quantity, Boolean included) {
    }

    public record InvoiceRequestBody(LocalDate dueDate, String notes) {
    }
}
