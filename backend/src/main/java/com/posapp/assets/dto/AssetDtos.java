package com.posapp.assets.dto;

import com.posapp.assets.AssetCategory;
import com.posapp.assets.AssetStatus;
import com.posapp.assets.DepreciationEntry;
import com.posapp.assets.DepreciationMethod;
import com.posapp.assets.FixedAsset;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record CategoryDto(Long id, String name, DepreciationMethod method, int usefulLifeMonths, BigDecimal salvagePercent,
                              boolean prorateFirstMonth, boolean active, long assetCount, Long assetAccountId, String assetAccountName,
                              Long accumulatedAccountId, String accumulatedAccountName, Long expenseAccountId, String expenseAccountName) {
        public static CategoryDto from(AssetCategory c, long assetCount) {
            return new CategoryDto(c.getId(), c.getName(), c.getMethod(), c.getUsefulLifeMonths(), c.getSalvagePercent(), c.isProrateFirstMonth(), c.isActive(), assetCount,
                    c.getAssetAccount() != null ? c.getAssetAccount().getId() : null, label(c.getAssetAccount()),
                    c.getAccumulatedAccount() != null ? c.getAccumulatedAccount().getId() : null, label(c.getAccumulatedAccount()),
                    c.getExpenseAccount() != null ? c.getExpenseAccount().getId() : null, label(c.getExpenseAccount()));
        }

        private static String label(com.posapp.accounting.Account a) {
            return a == null ? null : a.getCode() + " " + a.getName();
        }
    }

    public record AssetDto(Long id, String code, String name, Long categoryId, String categoryName, LocalDate purchaseDate,
                           BigDecimal cost, BigDecimal salvageValue, int usefulLifeMonths, DepreciationMethod method,
                           LocalDate depreciationStartDate, AssetStatus status, String location, String assignedTo,
                           String serialNumber, String supplierName, String notes, BigDecimal accumulatedDepreciation,
                           BigDecimal bookValue, LocalDate lastDepreciatedThrough, boolean acquisitionPosted,
                           boolean hasDepreciation, LocalDate disposalDate, BigDecimal disposalProceeds,
                           BigDecimal disposalGainLoss, String disposalNote, boolean prorateFirstMonth,
                           Long purchaseOrderId, String purchaseOrderNumber, Long branchId, String branchName,
                           LocalDate warrantyExpiresOn, LocalDate insuranceExpiresOn, String insurancePolicy, Long custodianUserId,
                           String custodianName, LocalDate checkedOutOn, BigDecimal originalCost, long fileCount,
                           String warrantyState, String insuranceState, BigDecimal nextCharge, Integer monthsLeft,
                           boolean rebased) {
        public static AssetDto from(FixedAsset a, boolean hasDepreciation, String branchName, long fileCount, String warrantyState,
                                    String insuranceState, BigDecimal nextCharge, Integer monthsLeft) {
            return new AssetDto(a.getId(), a.getCode(), a.getName(), a.getCategory().getId(), a.getCategory().getName(),
                    a.getPurchaseDate(), a.getCost(), a.getSalvageValue(), a.getUsefulLifeMonths(), a.getMethod(),
                    a.getDepreciationStartDate(), a.getStatus(), a.getLocation(), a.getAssignedTo(), a.getSerialNumber(),
                    a.getSupplierName(), a.getNotes(), a.getAccumulatedDepreciation(), a.bookValue(),
                    a.getLastDepreciatedThrough(), a.isAcquisitionPosted(), hasDepreciation, a.getDisposalDate(),
                    a.getDisposalProceeds(), a.getDisposalGainLoss(), a.getDisposalNote(), a.isProrateFirstMonth(),
                    a.getPurchaseOrderId(), a.getPurchaseOrderNumber(), a.getBranchId(), branchName,
                    a.getWarrantyExpiresOn(), a.getInsuranceExpiresOn(), a.getInsurancePolicy(), a.getCustodianUserId(),
                    a.getCustodianName(), a.getCheckedOutOn(), a.getOriginalCost(), fileCount, warrantyState, insuranceState,
                    nextCharge, monthsLeft, a.getMonthlyCharge() != null || a.getRateLifeMonths() != null);
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

    public record AdjustmentDto(Long id, String kind, LocalDate adjustmentDate, BigDecimal amount, String funding, Integer remainingLifeMonths,
                                BigDecimal salvageValue, String method, BigDecimal costAfter, BigDecimal bookValueAfter, String note, String createdByName) {
        public static AdjustmentDto from(com.posapp.assets.AssetAdjustment a) {
            return new AdjustmentDto(a.getId(), a.getKind(), a.getAdjustmentDate(), a.getAmount(), a.getFunding(), a.getRemainingLifeMonths(),
                    a.getSalvageValue(), a.getMethod(), a.getCostAfter(), a.getBookValueAfter(), a.getNote(), a.getCreatedByName());
        }
    }

    public record CustodyDto(Long id, Long custodianUserId, String custodianName, LocalDate checkedOutOn, LocalDate checkedInOn,
                             String outCondition, String inCondition, String note, String createdByName) {
        public static CustodyDto from(com.posapp.assets.AssetCustody c) {
            return new CustodyDto(c.getId(), c.getCustodianUserId(), c.getCustodianName(), c.getCheckedOutOn(), c.getCheckedInOn(),
                    c.getOutCondition(), c.getInCondition(), c.getNote(), c.getCreatedByName());
        }
    }

    public record EventDto(Long id, String kind, String detail, String createdByName, java.time.Instant createdAt) {
        public static EventDto from(com.posapp.assets.AssetEvent e) {
            return new EventDto(e.getId(), e.getKind(), e.getDetail(), e.getCreatedByName(), e.getCreatedAt());
        }
    }

    public record BulkSkipped(Long id, String code, String reason) {
    }

    public record BulkResult(int done, List<BulkSkipped> skipped) {
    }

    public record VerificationDto(Long id, String name, String status, Long branchId, String branchName, Long categoryId, String categoryName,
                                  String location, LocalDate startedOn, LocalDate closedOn, String note, String createdByName, String closedByName,
                                  long total, long pending, long found, long missing, long damaged) {
    }

    public record VerificationLineDto(Long id, Long assetId, String assetCode, String assetName, String expectedLocation, String expectedCustodian,
                                      BigDecimal bookValue, String result, String foundLocation, String note, java.time.Instant checkedAt,
                                      String checkedByName, boolean writtenOff, String assetStatus) {
    }

    public record NamedAmount(String name, long count, BigDecimal cost, BigDecimal bookValue) {
    }

    public record AttentionItem(Long id, String code, String name, String detail, LocalDate date, Integer days) {
    }

    /** The Assets overview page. */
    public record OverviewDto(long assetCount, BigDecimal totalCost, BigDecimal totalAccumulated, BigDecimal totalBookValue,
                              long fullyDepreciatedCount, long disposedCount, BigDecimal nextMonthCharge, LocalDate depreciatedThrough,
                              int monthsBehind, long withoutCustodian, long openVerifications, int alertDays, int soonMonths,
                              List<NamedAmount> byCategory, List<NamedAmount> byBranch, List<AttentionItem> fullyDepreciatingSoon,
                              List<AttentionItem> warrantyExpiring, List<AttentionItem> insuranceExpiring) {
    }
}
