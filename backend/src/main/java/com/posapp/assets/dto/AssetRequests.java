package com.posapp.assets.dto;

import com.posapp.assets.AcquisitionFunding;
import com.posapp.assets.DepreciationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AssetRequests {

    private AssetRequests() {
    }

    public record CategoryRequest(@NotBlank String name, @NotNull DepreciationMethod method, @NotNull Integer usefulLifeMonths,
                                  BigDecimal salvagePercent, Boolean prorateFirstMonth, Boolean active,
                                  Long assetAccountId, Long accumulatedAccountId, Long expenseAccountId) {
    }

    /**
     * Register or edit an asset. Method, life, salvage value and start date fall back to the category's defaults when
     * left empty. {@code acquisitionFunding} and {@code openingAccumulatedDepreciation} are used when registering only.
     */
    public record AssetRequest(String code, @NotBlank String name, @NotNull Long categoryId, @NotNull LocalDate purchaseDate,
                               @NotNull BigDecimal cost, BigDecimal salvageValue, Integer usefulLifeMonths,
                               DepreciationMethod method, LocalDate depreciationStartDate, String location, String assignedTo,
                               String serialNumber, String supplierName, String notes, AcquisitionFunding acquisitionFunding,
                               BigDecimal openingAccumulatedDepreciation, Boolean prorateFirstMonth, Long branchId,
                               LocalDate warrantyExpiresOn, LocalDate insuranceExpiresOn, String insurancePolicy) {
    }

    /**
     * Register assets from a received purchase order line: {@code quantity} assets (one per unit), each costing the line's
     * net unit cost. Name, date and the depreciation terms fall back to the line / the category when left empty.
     */
    public record FromPurchaseRequest(@NotNull Long purchaseOrderLineId, @NotNull Integer quantity, @NotNull Long categoryId,
                                      String name, LocalDate purchaseDate, String location, String assignedTo, String notes,
                                      Boolean prorateFirstMonth) {
    }

    public record DisposeRequest(@NotNull LocalDate disposalDate, BigDecimal proceeds, String note) {
    }

    /** Move an asset to another branch and/or location. A missing {@code toBranchId} keeps the current branch. */
    public record TransferRequest(Long toBranchId, @NotNull LocalDate transferDate, String toLocation, String assignedTo, String reason) {
    }

    public record RunDepreciationRequest(LocalDate upTo) {
    }

    /** Cost added after registration; spreads over {@code remainingLifeMonths} (default: the life left). */
    public record ImprovementRequest(@NotNull LocalDate date, @NotNull BigDecimal amount, AcquisitionFunding funding, Integer remainingLifeMonths, String note) {
    }

    /** A write-down for damage or obsolescence; reduces book value by {@code amount}. */
    public record ImpairmentRequest(@NotNull LocalDate date, @NotNull BigDecimal amount, Integer remainingLifeMonths, @NotBlank String note) {
    }

    /** New remaining life, salvage value and/or method from {@code date}'s month on. */
    public record TermsRequest(@NotNull LocalDate date, @NotNull Integer remainingLifeMonths, BigDecimal salvageValue, DepreciationMethod method, String note) {
    }

    public record ReasonRequest(String reason) {
    }

    public record CheckOutRequest(Long custodianUserId, String custodianName, LocalDate date, String condition, String note) {
    }

    public record CheckInRequest(LocalDate date, String condition, String note) {
    }

    /** One action on several assets: TRANSFER (branch/location), CHECK_OUT (custodian) or CHECK_IN. */
    public record BulkRequest(@NotBlank String action, @NotNull java.util.List<Long> ids, Long toBranchId, String toLocation, LocalDate date,
                              Long custodianUserId, String custodianName, String reason) {
    }

    public record VerificationRequest(@NotBlank String name, Long branchId, Long categoryId, String location, String note) {
    }

    public record MarkRequest(@NotBlank String result, String foundLocation, String note) {
    }

    public record MarkRestRequest(@NotBlank String result) {
    }

    public record CloseVerificationRequest(Boolean applyLocations) {
    }
}
