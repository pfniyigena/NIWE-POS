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
                                   String location, String assignedTo, String serialNumber, LocalDate warrantyExpiresOn, LocalDate insuranceExpiresOn) {
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

    /** Assets grouped by where they are or who holds them. */
    public record AssetHolderRow(String holder, int assetCount, BigDecimal cost, BigDecimal accumulatedDepreciation, BigDecimal bookValue) {
    }

    /** One warranty or insurance end date that has passed or is close. */
    public record AssetExpiryRow(String code, String name, String categoryName, String branchName, String kind, LocalDate expiresOn, long daysLeft,
                                 String state, String detail) {
    }

    /** What the coming months will charge, assuming no asset is bought, sold or changed. */
    public record AssetForecastRow(String month, int assetCount, BigDecimal depreciation, BigDecimal bookValueEnd) {
    }

    /** Roll-forward of the register for a period: cost and accumulated depreciation from opening to closing, per category. */
    public record AssetMovementRow(String categoryName, BigDecimal openingCost, BigDecimal additions, BigDecimal improvements, BigDecimal disposals,
                                   BigDecimal closingCost, BigDecimal openingAccumulated, BigDecimal depreciation, BigDecimal disposalsAccumulated,
                                   BigDecimal closingAccumulated, BigDecimal closingBookValue) {
    }

    /** One asset in a verification round. */
    public record AssetVerificationRow(String round, String roundStatus, LocalDate startedOn, String code, String name, String expectedLocation,
                                       String expectedCustodian, String result, String foundLocation, String checkedBy, String note, String writtenOff) {
    }
}
