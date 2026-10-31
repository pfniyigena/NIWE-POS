package com.posapp.assets;

import com.posapp.accounting.AccountingService;
import com.posapp.assets.dto.AssetDtos.AdjustmentDto;
import com.posapp.assets.dto.AssetDtos.AssetDto;
import com.posapp.assets.dto.AssetRequests.ImpairmentRequest;
import com.posapp.assets.dto.AssetRequests.ImprovementRequest;
import com.posapp.assets.dto.AssetRequests.TermsRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Changes to an asset after it was registered: improvements (cost added), impairments (value written down), new depreciation
 * terms, taking back the last month of depreciation, and undoing a disposal.
 *
 * <p>Every change to cost or terms "re-bases" the depreciation: from the month of the change the asset is charged
 * (book value - salvage value) / remaining life each month (declining balance uses the remaining life as its rate life), and the
 * months already charged are never touched.
 */
@Service
public class AssetValueService {

    private static final BigDecimal CRUMB = new BigDecimal("0.005");

    private final FixedAssetRepository assetRepository;
    private final DepreciationEntryRepository entryRepository;
    private final AssetAdjustmentRepository adjustmentRepository;
    private final AssetDepreciationService depreciationService;
    private final FixedAssetService assetService;
    private final AccountingService accountingService;
    private final AssetHistoryService history;
    private final AuditLogService auditLogService;
    private final BranchAccessService branchAccessService;

    public AssetValueService(FixedAssetRepository assetRepository, DepreciationEntryRepository entryRepository,
                             AssetAdjustmentRepository adjustmentRepository, AssetDepreciationService depreciationService,
                             FixedAssetService assetService, AccountingService accountingService, AssetHistoryService history,
                             AuditLogService auditLogService, BranchAccessService branchAccessService) {
        this.assetRepository = assetRepository;
        this.entryRepository = entryRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.depreciationService = depreciationService;
        this.assetService = assetService;
        this.accountingService = accountingService;
        this.history = history;
        this.auditLogService = auditLogService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<AdjustmentDto> adjustmentsOf(Long assetId) {
        find(assetId);
        return adjustmentRepository.findByAssetIdOrderByAdjustmentDateDescIdDesc(assetId).stream().map(AdjustmentDto::from).toList();
    }

    // ---- improvement ----

    @Transactional
    public AssetDto improve(Long id, ImprovementRequest r) {
        FixedAsset asset = find(id);
        BigDecimal amount = money(r.amount(), "The cost");
        if (amount.signum() <= 0) throw ApiException.badRequest("The cost of the improvement must be more than zero.");
        prepare(asset, r.date());
        AcquisitionFunding funding = r.funding() != null ? r.funding() : AcquisitionFunding.NONE;
        asset.setCost(asset.getCost().add(amount));
        int life = remainingLife(asset, r.remainingLifeMonths());
        rebase(asset, life, null);
        assetRepository.save(asset);
        if (funding != AcquisitionFunding.NONE) {
            accountingService.postAssetImprovement(asset.getId(), "Improvement of " + asset.getCode() + " " + asset.getName(), amount,
                    funding == AcquisitionFunding.CASH, asset.getBranchId(), AssetDepreciationService.accountsOf(asset.getCategory()));
            asset.setAcquisitionPosted(true);
        }
        AssetAdjustment adj = record(asset, AssetAdjustment.IMPROVEMENT, r.date(), amount, funding.name(), life, null, null, r.note());
        history.event(asset.getId(), "IMPROVEMENT", "Added " + amount + " to the cost" + (funding == AcquisitionFunding.NONE ? " (already in the books)" : " paid " + funding.name().toLowerCase())
                + "; now depreciates over " + life + " more month(s)");
        auditLogService.record("ASSET_IMPROVED", "Improvement of " + amount + " on asset " + asset.getCode(), "FixedAsset", asset.getId());
        return assetService.toDto(assetRepository.save(asset));
    }

    // ---- impairment ----

    @Transactional
    public AssetDto impair(Long id, ImpairmentRequest r) {
        FixedAsset asset = find(id);
        BigDecimal amount = money(r.amount(), "The loss");
        if (amount.signum() <= 0) throw ApiException.badRequest("The loss must be more than zero.");
        if (r.note() == null || r.note().isBlank()) throw ApiException.badRequest("Say why the value is written down.");
        prepare(asset, r.date());
        BigDecimal room = asset.bookValue().subtract(asset.getSalvageValue());
        if (amount.compareTo(room) > 0) {
            throw ApiException.badRequest("The loss can be at most " + room.setScale(2, RoundingMode.HALF_UP)
                    + " (book value less salvage value). To write the whole asset off, dispose of it instead.");
        }
        asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(amount));
        int life = remainingLife(asset, r.remainingLifeMonths());
        rebase(asset, life, null);
        assetRepository.save(asset);
        accountingService.postAssetImpairment(asset.getId(), "Impairment of " + asset.getCode() + " " + asset.getName() + ": " + r.note().trim(), amount,
                asset.getBranchId(), AssetDepreciationService.accountsOf(asset.getCategory()));
        record(asset, AssetAdjustment.IMPAIRMENT, r.date(), amount, null, life, null, null, r.note());
        history.event(asset.getId(), "IMPAIRMENT", "Written down by " + amount + " - " + r.note().trim());
        auditLogService.record("ASSET_IMPAIRED", "Impairment of " + amount + " on asset " + asset.getCode(), "FixedAsset", asset.getId());
        return assetService.toDto(assetRepository.save(asset));
    }

    // ---- new terms ----

    @Transactional
    public AssetDto changeTerms(Long id, TermsRequest r) {
        FixedAsset asset = find(id);
        if (r.remainingLifeMonths() == null || r.remainingLifeMonths() < 1 || r.remainingLifeMonths() > 1200) {
            throw ApiException.badRequest("The remaining life must be from 1 to 1200 months.");
        }
        prepare(asset, r.date());
        if (r.salvageValue() != null) {
            BigDecimal salvage = money(r.salvageValue(), "The salvage value");
            if (salvage.signum() < 0 || salvage.compareTo(asset.bookValue()) >= 0) {
                throw ApiException.badRequest("The salvage value must be zero or more, and less than the book value (" + asset.bookValue() + ").");
            }
            asset.setSalvageValue(salvage);
        }
        if (r.method() != null) asset.setMethod(r.method());
        rebase(asset, r.remainingLifeMonths(), null);
        assetRepository.save(asset);
        record(asset, AssetAdjustment.TERMS, r.date(), BigDecimal.ZERO, null, r.remainingLifeMonths(), asset.getSalvageValue(), asset.getMethod().name(), r.note());
        history.event(asset.getId(), "TERMS", "New terms: " + r.remainingLifeMonths() + " month(s) left, salvage " + asset.getSalvageValue() + ", " + asset.getMethod().name().toLowerCase().replace('_', ' ')
                + (r.note() != null && !r.note().isBlank() ? " - " + r.note().trim() : ""));
        auditLogService.record("ASSET_TERMS_CHANGED", "New depreciation terms on asset " + asset.getCode(), "FixedAsset", asset.getId());
        return assetService.toDto(assetRepository.save(asset));
    }

    // ---- take back the last month ----

    /** Takes back the most recent month of depreciation of one asset (the books get the opposite entry). */
    @Transactional
    public AssetDto reverseLastDepreciation(Long id, String reason) {
        FixedAsset asset = find(id);
        if (asset.getStatus() == AssetStatus.DISPOSED) throw ApiException.conflict("A disposed asset can't be changed.");
        DepreciationEntry last = entryRepository.findFirstByAssetIdOrderByPeriodEndDescIdDesc(id)
                .orElseThrow(() -> ApiException.badRequest("No depreciation has been charged on this asset."));
        YearMonth month = YearMonth.from(last.getPeriodEnd());
        for (AssetAdjustment adj : adjustmentRepository.findByAssetIdOrderByAdjustmentDateAscIdAsc(id)) {
            if (YearMonth.from(adj.getAdjustmentDate()).isAfter(month)) {
                throw ApiException.conflict("A value change dated " + adj.getAdjustmentDate() + " comes after that month, so it can't be taken back.");
            }
        }
        DepreciationEntry previous = entryRepository.findByAssetIdOrderByPeriodEndAsc(id).stream()
                .filter(e -> e.getPeriodEnd().isBefore(last.getPeriodEnd())).reduce((a, b) -> b).orElse(null);
        accountingService.postAssetDepreciation(month.getYear() * 100L + month.getMonthValue(), "Depreciation for " + month + " taken back on " + asset.getCode(),
                last.getAmount(), last.getBranchId(), last.getCategoryId() != null ? AssetDepreciationService.accountsOf(asset.getCategory()) : AccountingService.AssetAccounts.STANDARD, true);
        asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().subtract(last.getAmount()));
        asset.setLastDepreciatedThrough(previous != null ? previous.getPeriodEnd() : null);
        asset.setStatus(AssetStatus.ACTIVE);
        entryRepository.delete(last);
        assetRepository.save(asset);
        history.event(id, "DEPRECIATION_BACK", "Took back " + last.getAmount() + " depreciation for " + month + (reason != null && !reason.isBlank() ? " - " + reason.trim() : ""));
        auditLogService.record("ASSET_DEPRECIATION_REVERSED", "Took back depreciation of " + last.getAmount() + " for " + month + " on asset " + asset.getCode(), "FixedAsset", id);
        return assetService.toDto(asset);
    }

    // ---- undo a disposal ----

    /** Puts a disposed asset back on the register and reverses the disposal entry. Needs the disposal approval right. */
    @Transactional
    public AssetDto undoDisposal(Long id, String reason) {
        FixedAsset asset = find(id);
        if (asset.getStatus() != AssetStatus.DISPOSED) throw ApiException.conflict("This asset has not been disposed.");
        if (!AssetHistoryService.actor().disposalApprover()) {
            throw ApiException.forbidden("Undoing a disposal needs the 'Approve large asset disposals' permission.");
        }
        BigDecimal proceeds = asset.getDisposalProceeds() != null ? asset.getDisposalProceeds() : BigDecimal.ZERO;
        accountingService.postAssetDisposal(asset.getId(), "Disposal of asset " + asset.getCode() + " " + asset.getName(), asset.getCost(),
                asset.getAccumulatedDepreciation(), proceeds, asset.getBranchId(), AssetDepreciationService.accountsOf(asset.getCategory()), true);
        LocalDate was = asset.getDisposalDate();
        asset.setDisposalDate(null);
        asset.setDisposalProceeds(null);
        asset.setDisposalGainLoss(null);
        asset.setDisposalNote(null);
        asset.setDisposalUndoneCount(asset.getDisposalUndoneCount() + 1);
        BigDecimal left = asset.getCost().subtract(asset.getSalvageValue()).subtract(asset.getAccumulatedDepreciation());
        asset.setStatus(left.compareTo(CRUMB) < 0 ? AssetStatus.FULLY_DEPRECIATED : AssetStatus.ACTIVE);
        assetRepository.save(asset);
        history.event(id, "DISPOSAL_UNDONE", "Disposal of " + was + " undone" + (reason != null && !reason.isBlank() ? " - " + reason.trim() : ""));
        auditLogService.record("ASSET_DISPOSAL_UNDONE", "Undid the disposal of asset " + asset.getCode(), "FixedAsset", id);
        return assetService.toDto(asset);
    }

    // ---- helpers ----

    private FixedAsset find(Long id) {
        FixedAsset asset = assetRepository.findById(id).orElseThrow(() -> ApiException.notFound("Asset not found: " + id));
        branchAccessService.assertCanAccess(asset.getBranchId());
        return asset;
    }

    /** Date checks and catching up depreciation to the end of the month before the change, so the change starts clean. */
    private void prepare(FixedAsset asset, LocalDate date) {
        if (asset.getStatus() == AssetStatus.DISPOSED) throw ApiException.conflict("A disposed asset can't be changed.");
        if (date == null) throw ApiException.badRequest("Choose the date of the change.");
        if (date.isBefore(asset.getPurchaseDate())) throw ApiException.badRequest("The date can't be before the purchase date.");
        if (date.isAfter(LocalDate.now())) throw ApiException.badRequest("The date can't be in the future.");
        if (asset.getStatus() == AssetStatus.ACTIVE) {
            depreciationService.runFor(asset, date.withDayOfMonth(1).minusDays(1));
        }
        LocalDate through = asset.getLastDepreciatedThrough();
        if (through != null && !through.isBefore(date.withDayOfMonth(1))) {
            throw ApiException.badRequest("Depreciation has already been charged up to " + through + ". Use a date after that, or take the last month back first.");
        }
    }

    /** Months left: what the caller gave, otherwise the useful life less the months already charged. */
    private int remainingLife(FixedAsset asset, Integer given) {
        if (given != null) {
            if (given < 1 || given > 1200) throw ApiException.badRequest("The remaining life must be from 1 to 1200 months.");
            return given;
        }
        long charged = entryRepository.countByAssetId(asset.getId());
        long left = asset.getUsefulLifeMonths() - charged;
        if (left < 1) throw ApiException.badRequest("Enter how many months the asset will now last.");
        return (int) left;
    }

    /** Sets the charge for the months to come from the asset's current book value, salvage value and the months left. */
    private void rebase(FixedAsset asset, int lifeMonths, Integer unused) {
        BigDecimal depreciable = asset.getCost().subtract(asset.getSalvageValue()).subtract(asset.getAccumulatedDepreciation());
        long charged = entryRepository.countByAssetId(asset.getId());
        asset.setUsefulLifeMonths((int) Math.min(1200, charged + lifeMonths));
        asset.setRateLifeMonths(lifeMonths);
        if (depreciable.compareTo(CRUMB) < 0) {
            asset.setMonthlyCharge(null);
            asset.setStatus(AssetStatus.FULLY_DEPRECIATED);
            return;
        }
        asset.setMonthlyCharge(depreciable.divide(BigDecimal.valueOf(lifeMonths), 2, RoundingMode.HALF_UP));
        asset.setStatus(AssetStatus.ACTIVE);
    }

    private AssetAdjustment record(FixedAsset asset, String kind, LocalDate date, BigDecimal amount, String funding, Integer life,
                                   BigDecimal salvage, String method, String note) {
        AssetAdjustment a = new AssetAdjustment();
        a.setAssetId(asset.getId());
        a.setKind(kind);
        a.setAdjustmentDate(date);
        a.setAmount(amount);
        a.setFunding(funding);
        a.setRemainingLifeMonths(life);
        a.setSalvageValue(salvage);
        a.setMethod(method);
        a.setCostAfter(asset.getCost());
        a.setBookValueAfter(asset.bookValue());
        a.setNote(note != null && !note.isBlank() ? (note.trim().length() > 1000 ? note.trim().substring(0, 1000) : note.trim()) : null);
        a.setCreatedByName(AssetHistoryService.actor().name());
        return adjustmentRepository.save(a);
    }

    private static BigDecimal money(BigDecimal v, String what) {
        if (v == null) throw ApiException.badRequest(what + " is required.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
