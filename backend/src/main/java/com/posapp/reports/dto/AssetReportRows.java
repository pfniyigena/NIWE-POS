package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Row types of the Assets reports (Assets > Reports), used by {@code AssetReportService} and the matching {@code ReportController} endpoints. */
public final class AssetReportRows {

    private AssetReportRows() {
    }

    /** One asset in the register, as it stands today. */
    public record AssetRegisterRow(String code, String name, String categoryName, String status, LocalDate purchaseDate,
                                   BigDecimal cost, BigDecimal accumulatedDepreciation, BigDecimal bookValue, String branchName,
                                   String location, String assignedTo, String serialNumber) {
    }

    /** One month's charge on one asset. */
    public record DepreciationScheduleRow(LocalDate periodEnd, String code, String name, String categoryName, String branchName, BigDecimal amount,
                                          BigDecimal accumulatedAfter, BigDecimal bookValueAfter) {
    }

    public record AssetDisposalRow(LocalDate disposalDate, String code, String name, String categoryName, String branchName, BigDecimal cost,
                                   BigDecimal accumulatedDepreciation, BigDecimal bookValue, BigDecimal proceeds, BigDecimal gainLoss) {
    }

    /** One move of an asset between branches and/or locations. */
    public record AssetTransferRow(LocalDate transferDate, String code, String name, String fromBranch, String toBranch,
                                   String fromLocation, String toLocation, String reason) {
    }

    /** Assets still owned (not disposed), summed per category. */
    public record AssetCategorySummaryRow(String categoryName, int assetCount, BigDecimal cost, BigDecimal accumulatedDepreciation,
                                          BigDecimal bookValue) {
    }
}
