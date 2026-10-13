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
                                  BigDecimal salvagePercent, Boolean prorateFirstMonth, Boolean active) {
    }

    /**
     * Register or edit an asset. Method, life, salvage value and start date fall back to the category's defaults when
     * left empty. {@code acquisitionFunding} and {@code openingAccumulatedDepreciation} are used when registering only.
     */
    public record AssetRequest(String code, @NotBlank String name, @NotNull Long categoryId, @NotNull LocalDate purchaseDate,
                               @NotNull BigDecimal cost, BigDecimal salvageValue, Integer usefulLifeMonths,
                               DepreciationMethod method, LocalDate depreciationStartDate, String location, String assignedTo,
                               String serialNumber, String supplierName, String notes, AcquisitionFunding acquisitionFunding,
                               BigDecimal openingAccumulatedDepreciation, Boolean prorateFirstMonth, Long branchId) {
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
}
