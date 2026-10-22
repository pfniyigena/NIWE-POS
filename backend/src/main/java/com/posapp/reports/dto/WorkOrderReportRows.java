package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rows of the work order reports. */
public final class WorkOrderReportRows {

    private WorkOrderReportRows() {
    }

    public record WorkOrderRow(String number, String type, String status, String priority, String title, String customer, String asset, String assignedTo,
                               LocalDate requested, LocalDate due, BigDecimal hours, BigDecimal partsCost, BigDecimal externalCost, String invoice) {
    }

    public record ProfitRow(String number, String title, String customer, LocalDate completed, BigDecimal revenue, BigDecimal partsCost, BigDecimal externalCost,
                            BigDecimal hours, BigDecimal margin, BigDecimal marginPercent) {
    }

    public record AssetCostRow(String asset, int jobs, BigDecimal hours, BigDecimal partsCost, BigDecimal externalCost, BigDecimal totalCost, LocalDate lastJob) {
    }

    public record OverdueRow(String number, String title, String customer, String assignedTo, String priority, String status, LocalDate due, long daysOverdue) {
    }
}
