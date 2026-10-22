package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.CostBearer;
import com.posapp.propertymgmt.MaintenanceCategory;
import com.posapp.propertymgmt.MaintenancePriority;
import com.posapp.propertymgmt.MaintenanceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the maintenance screens read. */
public final class PropertyMgmtMaintenanceDtos {

    private PropertyMgmtMaintenanceDtos() {
    }

    /** daysOpen: days since it was reported while it is open, or from report to completion once done. overdue: still open past its due date. */
    public record MaintenanceRow(Long id, String code, Long unitId, String unitNo, Long propertyId, String propertyName, Long ownerId, String ownerName, Long tenantId, String tenantName,
                                 String title, MaintenanceCategory category, MaintenancePriority priority, MaintenanceStatus status, LocalDate reportedDate, LocalDate scheduledDate,
                                 LocalDate dueDate, LocalDate completedDate, Long daysOpen, boolean overdue, String contractorName, BigDecimal estimatedCost, BigDecimal actualCost,
                                 CostBearer costBearer, String workOrderNumber, String invoiceNumber) {
    }

    /**
     * workOrdersEnabled: the Work Orders module is on, so a work order can be raised. canInvoiceTenant: done, tenant-paid, with a cost, no invoice yet.
     * invoiceStatus is the status of the invoice sent to the tenant (null when none, or when it was voided).
     */
    public record MaintenanceDto(MaintenanceRow request, String description, String reportedBy, String contractorPhone, LocalDate startedDate, String resolution, Long leaseId,
                                 String leaseCode, Long workOrderId, String workOrderStatus, Long invoiceId, String invoiceStatus, BigDecimal invoiceTotal, BigDecimal invoicePaid,
                                 BigDecimal invoiceOutstanding, boolean workOrdersEnabled, boolean canInvoiceTenant, boolean canManage, String createdByName, Instant createdAt) {
    }

    public record MaintenanceSummary(long open, long urgent, long overdue, List<MaintenanceRow> attention) {
    }

    /** Repair cost per property, by who pays; only requests that are done count. */
    public record MaintenanceCostRow(Long propertyId, String propertyName, String ownerName, long requests, long completed, BigDecimal owner, BigDecimal tenant, BigDecimal agency,
                                     BigDecimal total) {
    }
}
