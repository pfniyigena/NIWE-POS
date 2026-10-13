package com.posapp.assets.dto;

import com.posapp.assets.AssetCategory;
import com.posapp.assets.AssetStatus;
import com.posapp.assets.DepreciationEntry;
import com.posapp.assets.DepreciationMethod;
import com.posapp.assets.FixedAsset;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record CategoryDto(Long id, String name, DepreciationMethod method, int usefulLifeMonths, BigDecimal salvagePercent,
                              boolean prorateFirstMonth, boolean active, long assetCount) {
        public static CategoryDto from(AssetCategory c, long assetCount) {
            return new CategoryDto(c.getId(), c.getName(), c.getMethod(), c.getUsefulLifeMonths(), c.getSalvagePercent(), c.isProrateFirstMonth(), c.isActive(), assetCount);
        }
    }

    public record AssetDto(Long id, String code, String name, Long categoryId, String categoryName, LocalDate purchaseDate,
                           BigDecimal cost, BigDecimal salvageValue, int usefulLifeMonths, DepreciationMethod method,
                           LocalDate depreciationStartDate, AssetStatus status, String location, String assignedTo,
                           String serialNumber, String supplierName, String notes, BigDecimal accumulatedDepreciation,
                           BigDecimal bookValue, LocalDate lastDepreciatedThrough, boolean acquisitionPosted,
                           boolean hasDepreciation, LocalDate disposalDate, BigDecimal disposalProceeds,
                           BigDecimal disposalGainLoss, String disposalNote, boolean prorateFirstMonth,
                           Long purchaseOrderId, String purchaseOrderNumber, Long branchId, String branchName) {
        public static AssetDto from(FixedAsset a, boolean hasDepreciation, String branchName) {
            return new AssetDto(a.getId(), a.getCode(), a.getName(), a.getCategory().getId(), a.getCategory().getName(),
                    a.getPurchaseDate(), a.getCost(), a.getSalvageValue(), a.getUsefulLifeMonths(), a.getMethod(),
                    a.getDepreciationStartDate(), a.getStatus(), a.getLocation(), a.getAssignedTo(), a.getSerialNumber(),
                    a.getSupplierName(), a.getNotes(), a.getAccumulatedDepreciation(), a.bookValue(),
                    a.getLastDepreciatedThrough(), a.isAcquisitionPosted(), hasDepreciation, a.getDisposalDate(),
                    a.getDisposalProceeds(), a.getDisposalGainLoss(), a.getDisposalNote(), a.isProrateFirstMonth(),
                    a.getPurchaseOrderId(), a.getPurchaseOrderNumber(), a.getBranchId(), branchName);
        }
    }

    public record DepreciationEntryDto(Long id, Long assetId, String assetCode, String assetName, LocalDate periodEnd,
                                       BigDecimal amount, BigDecimal accumulatedAfter, BigDecimal bookValueAfter) {
        public static DepreciationEntryDto from(DepreciationEntry e) {
            return new DepreciationEntryDto(e.getId(), e.getAsset().getId(), e.getAsset().getCode(), e.getAsset().getName(),
                    e.getPeriodEnd(), e.getAmount(), e.getAccumulatedAfter(), e.getBookValueAfter());
        }
    }

    /** Totals shown above the register. Disposed assets are not counted. */
    public record SummaryDto(long assetCount, BigDecimal totalCost, BigDecimal totalAccumulatedDepreciation, BigDecimal totalBookValue,
                             long fullyDepreciatedCount, long disposedCount) {
    }

    public record TransferDto(Long id, Long assetId, String assetCode, String assetName, LocalDate transferDate, String fromBranchName,
                              String toBranchName, String fromLocation, String toLocation, String reason) {
        public static TransferDto from(com.posapp.assets.AssetTransfer t) {
            return new TransferDto(t.getId(), t.getAsset().getId(), t.getAsset().getCode(), t.getAsset().getName(), t.getTransferDate(),
                    t.getFromBranchName(), t.getToBranchName(), t.getFromLocation(), t.getToLocation(), t.getReason());
        }
    }

    /** A received purchase order line that can be turned into assets. */
    public record PurchaseLineDto(Long lineId, Long purchaseOrderId, String purchaseOrderNumber, String supplierName,
                                  LocalDate orderDate, String productName, int quantityReceived, int quantityRegistered,
                                  BigDecimal unitNetCost, boolean stockTracked) {
    }

    public record DepreciationRunResult(int monthsPosted, int entriesCreated, BigDecimal totalAmount, LocalDate upTo) {
    }
}
