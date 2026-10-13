package com.posapp.assets;

import com.posapp.accounting.AccountingService;
import com.posapp.assets.dto.AssetDtos.*;
import com.posapp.assets.dto.AssetRequests.*;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The asset register: categories, assets (register, edit, delete) and disposal. */
@Service
public class FixedAssetService {

    private final AssetCategoryRepository categoryRepository;
    private final FixedAssetRepository assetRepository;
    private final DepreciationEntryRepository entryRepository;
    private final AssetDepreciationService depreciationService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final BranchService branchService;
    private final BranchAccessService branchAccessService;
    private final AssetTransferRepository transferRepository;

    public FixedAssetService(AssetCategoryRepository categoryRepository, FixedAssetRepository assetRepository,
                             DepreciationEntryRepository entryRepository, AssetDepreciationService depreciationService,
                             AccountingService accountingService, AuditLogService auditLogService, BranchService branchService,
                             BranchAccessService branchAccessService, AssetTransferRepository transferRepository) {
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
        this.transferRepository = transferRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.entryRepository = entryRepository;
        this.depreciationService = depreciationService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
    }

    // ---- categories ----

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CategoryDto.from(c, assetRepository.countByCategoryId(c.getId()))).toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("There is already an asset category called " + name + ".");
        }
        AssetCategory category = new AssetCategory();
        applyCategory(category, request, name);
        AssetCategory saved = categoryRepository.save(category);
        auditLogService.record("ASSET_CATEGORY_CREATED", "Created asset category " + saved.getName(), "AssetCategory", saved.getId());
        return CategoryDto.from(saved, 0);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest request) {
        AssetCategory category = categoryRepository.findById(id).orElseThrow(() -> ApiException.notFound("Asset category not found: " + id));
        String name = request.name().trim();
        if (!category.getName().equalsIgnoreCase(name) && categoryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("There is already an asset category called " + name + ".");
        }
        applyCategory(category, request, name);
        AssetCategory saved = categoryRepository.save(category);
        auditLogService.record("ASSET_CATEGORY_UPDATED", "Updated asset category " + saved.getName(), "AssetCategory", saved.getId());
        return CategoryDto.from(saved, assetRepository.countByCategoryId(id));
    }

    private void applyCategory(AssetCategory category, CategoryRequest request, String name) {
        if (request.usefulLifeMonths() < 1) {
            throw ApiException.badRequest("Useful life must be at least 1 month.");
        }
        BigDecimal salvage = request.salvagePercent() != null ? request.salvagePercent() : BigDecimal.ZERO;
        if (salvage.signum() < 0 || salvage.compareTo(new BigDecimal("100")) >= 0) {
            throw ApiException.badRequest("Salvage percent must be from 0 to less than 100.");
        }
        category.setName(name);
        category.setMethod(request.method());
        category.setUsefulLifeMonths(request.usefulLifeMonths());
        category.setSalvagePercent(salvage);
        category.setProrateFirstMonth(Boolean.TRUE.equals(request.prorateFirstMonth()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    // ---- assets ----

    @Transactional(readOnly = true)
    public List<AssetDto> list(AssetStatus status, Long branchId) {
        Map<Long, String> names = branchNames();
        return visibleAssets(status, branchId).stream().map(a -> toDto(a, names)).toList();
    }

    /** Assets of one status (or all), optionally of one branch, limited to the branches the signed-in user may work with. */
    private List<FixedAsset> visibleAssets(AssetStatus status, Long branchId) {
        List<FixedAsset> assets = status == null ? assetRepository.findAllByOrderByCodeAsc() : assetRepository.findByStatusOrderByCodeAsc(status);
        Long hq = branchService.headquartersId();
        return assets.stream()
                .filter(a -> branchId == null || effectiveBranch(a, hq).equals(branchId))
                .filter(a -> branchAccessService.canAccess(a.getBranchId()))
                .toList();
    }

    private static Long effectiveBranch(FixedAsset asset, Long headquartersId) {
        return asset.getBranchId() != null ? asset.getBranchId() : headquartersId;
    }

    @Transactional(readOnly = true)
    public AssetDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<TransferDto> transfersOf(Long id) {
        find(id);
        return transferRepository.findByAssetIdOrderByTransferDateDescIdDesc(id).stream().map(TransferDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DepreciationEntryDto> depreciationOf(Long id) {
        find(id);
        return entryRepository.findByAssetIdOrderByPeriodEndAsc(id).stream().map(DepreciationEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public SummaryDto summary(Long branchId) {
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal accumulated = BigDecimal.ZERO;
        long count = 0;
        long fully = 0;
        long disposed = 0;
        for (FixedAsset a : visibleAssets(null, branchId)) {
            if (a.getStatus() == AssetStatus.DISPOSED) {
                disposed++;
                continue;
            }
            count++;
            if (a.getStatus() == AssetStatus.FULLY_DEPRECIATED) fully++;
            cost = cost.add(a.getCost());
            accumulated = accumulated.add(a.getAccumulatedDepreciation());
        }
        return new SummaryDto(count, cost, accumulated, cost.subtract(accumulated), fully, disposed);
    }

    @Transactional
    public AssetDto create(AssetRequest request) {
        return toDto(createAsset(request, null));
    }

    /** Where a linked asset came from: the received purchase order line. */
    record PurchaseLink(Long orderId, String orderNumber, Long lineId) {
    }

    /** Registers one asset (optionally linked to a purchase order line, in which case its purchase counts as already posted). */
    FixedAsset createAsset(AssetRequest request, PurchaseLink link) {
        AssetCategory category = activeCategory(request.categoryId());
        validateMoney(request);
        FixedAsset asset = new FixedAsset();
        asset.setCategory(category);
        String code = request.code() != null ? request.code().trim() : "";
        if (code.isEmpty()) {
            code = nextCode();
        } else if (assetRepository.existsByCode(code)) {
            throw ApiException.conflict("There is already an asset with the tag " + code + ".");
        }
        asset.setCode(code);
        Long branch = branchService.resolveForDocument(request.branchId());
        branchAccessService.assertCanAccess(branch);
        asset.setBranchId(branch);
        applyDetails(asset, request);
        applyDepreciationTerms(asset, request, category);
        BigDecimal opening = request.openingAccumulatedDepreciation() != null ? request.openingAccumulatedDepreciation() : BigDecimal.ZERO;
        if (opening.signum() < 0 || opening.compareTo(asset.getCost().subtract(asset.getSalvageValue())) > 0) {
            throw ApiException.badRequest("Depreciation already charged can't be negative or more than cost minus salvage value.");
        }
        asset.setAccumulatedDepreciation(opening.setScale(2, RoundingMode.HALF_UP));
        if (asset.getCost().subtract(asset.getSalvageValue()).subtract(asset.getAccumulatedDepreciation()).signum() <= 0) {
            asset.setStatus(AssetStatus.FULLY_DEPRECIATED);
        }
        if (link != null) {
            asset.setPurchaseOrderId(link.orderId());
            asset.setPurchaseOrderNumber(link.orderNumber());
            asset.setPurchaseOrderLineId(link.lineId());
            asset.setAcquisitionPosted(true); // the caller posts the Inventory -> Fixed Assets reclassification
        }
        return persistNew(asset, link != null ? AcquisitionFunding.NONE : request.acquisitionFunding());
    }

    private FixedAsset persistNew(FixedAsset asset, AcquisitionFunding funding) {
        FixedAsset saved = assetRepository.save(asset);
        AcquisitionFunding how = funding != null ? funding : AcquisitionFunding.NONE;
        if (how != AcquisitionFunding.NONE) {
            accountingService.postAssetAcquisition(saved.getId(), "Asset purchase " + saved.getCode() + " " + saved.getName(),
                    saved.getCost(), how == AcquisitionFunding.CASH, saved.getBranchId());
            saved.setAcquisitionPosted(true);
            saved = assetRepository.save(saved);
        }
        auditLogService.record("ASSET_REGISTERED", "Registered asset " + saved.getCode() + " " + saved.getName() + " (cost " + saved.getCost() + ")",
                "FixedAsset", saved.getId());
        return saved;
    }

    @Transactional
    public AssetDto update(Long id, AssetRequest request) {
        FixedAsset asset = find(id);
        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.conflict("A disposed asset can't be changed.");
        }
        applyDetails(asset, request);
        String code = request.code() != null ? request.code().trim() : "";
        if (!code.isEmpty() && !code.equals(asset.getCode())) {
            if (assetRepository.existsByCode(code)) {
                throw ApiException.conflict("There is already an asset with the tag " + code + ".");
            }
            asset.setCode(code);
        }
        boolean locked = asset.getLastDepreciatedThrough() != null || asset.isAcquisitionPosted();
        if (!locked) {
            AssetCategory category = request.categoryId().equals(asset.getCategory().getId())
                    ? asset.getCategory() : activeCategory(request.categoryId());
            validateMoney(request);
            asset.setCategory(category);
            applyDepreciationTerms(asset, request, category);
        } else if (asset.getCost().compareTo(request.cost()) != 0 || !asset.getPurchaseDate().equals(request.purchaseDate())) {
            throw ApiException.conflict("Cost and purchase date can't be changed once depreciation or the purchase has been posted to Accounting.");
        }
        FixedAsset saved = assetRepository.save(asset);
        auditLogService.record("ASSET_UPDATED", "Updated asset " + saved.getCode() + " " + saved.getName(), "FixedAsset", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        FixedAsset asset = find(id);
        if (asset.getLastDepreciatedThrough() != null || asset.isAcquisitionPosted() || asset.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.conflict("This asset has accounting history (depreciation, purchase posting or disposal) and can't be deleted.");
        }
        assetRepository.delete(asset);
        auditLogService.record("ASSET_DELETED", "Deleted asset " + asset.getCode() + " " + asset.getName(), "FixedAsset", id);
    }

    /**
     * Sells or writes off an asset. Depreciation is charged first for every full month before the disposal month, then
     * Accounting gets: cash received, accumulated depreciation cleared, cost removed, and the gain or loss.
     */
    @Transactional
    public AssetDto dispose(Long id, DisposeRequest request) {
        FixedAsset asset = find(id);
        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.conflict("This asset is already disposed.");
        }
        LocalDate date = request.disposalDate();
        if (date.isBefore(asset.getPurchaseDate())) {
            throw ApiException.badRequest("The disposal date can't be before the purchase date.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("The disposal date can't be in the future.");
        }
        BigDecimal proceeds = request.proceeds() != null ? request.proceeds().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (proceeds.signum() < 0) {
            throw ApiException.badRequest("Proceeds can't be negative.");
        }
        if (asset.getStatus() == AssetStatus.ACTIVE) {
            depreciationService.runFor(asset, date.withDayOfMonth(1).minusDays(1));
        }
        BigDecimal bookValue = asset.bookValue();
        asset.setDisposalDate(date);
        asset.setDisposalProceeds(proceeds);
        asset.setDisposalGainLoss(proceeds.subtract(bookValue));
        asset.setDisposalNote(request.note());
        asset.setStatus(AssetStatus.DISPOSED);
        FixedAsset saved = assetRepository.save(asset);
        accountingService.postAssetDisposal(saved.getId(), "Disposal of asset " + saved.getCode() + " " + saved.getName(),
                saved.getCost(), saved.getAccumulatedDepreciation(), proceeds, saved.getBranchId());
        auditLogService.record("ASSET_DISPOSED", "Disposed asset " + saved.getCode() + " " + saved.getName() + " for " + proceeds
                + " (gain/loss " + saved.getDisposalGainLoss() + ")", "FixedAsset", saved.getId());
        return toDto(saved);
    }

    /**
     * Moves an asset to another branch and/or location. Depreciation is charged first for every full month before the
     * transfer month, so those months stay with the old branch; later months are charged to the new one. Accounting is not
     * re-posted: past entries keep the old branch, and (like stock transfers) no inter-branch entry is made.
     */
    @Transactional
    public AssetDto transfer(Long id, TransferRequest request) {
        FixedAsset asset = find(id);
        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.conflict("A disposed asset can't be transferred.");
        }
        LocalDate date = request.transferDate();
        if (date.isBefore(asset.getPurchaseDate())) {
            throw ApiException.badRequest("The transfer date can't be before the purchase date.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("The transfer date can't be in the future.");
        }
        Long hq = branchService.headquartersId();
        Long fromBranch = effectiveBranch(asset, hq);
        Long toBranch = request.toBranchId() != null ? branchService.resolveForDocument(request.toBranchId()) : fromBranch;
        branchAccessService.assertCanAccess(toBranch);
        String toLocation = blankToNull(request.toLocation());
        boolean branchChanged = !toBranch.equals(fromBranch);
        boolean locationChanged = toLocation != null && !toLocation.equals(asset.getLocation());
        if (!branchChanged && !locationChanged) {
            throw ApiException.badRequest("Choose a different branch or a new location.");
        }
        if (asset.getStatus() == AssetStatus.ACTIVE && branchChanged) {
            depreciationService.runFor(asset, date.withDayOfMonth(1).minusDays(1));
        }
        Map<Long, String> names = branchNames();
        AssetTransfer t = new AssetTransfer();
        t.setAsset(asset);
        t.setTransferDate(date);
        t.setFromBranchId(fromBranch);
        t.setFromBranchName(names.get(fromBranch));
        t.setToBranchId(toBranch);
        t.setToBranchName(names.get(toBranch));
        t.setFromLocation(asset.getLocation());
        t.setToLocation(toLocation != null ? toLocation : asset.getLocation());
        t.setReason(blankToNull(request.reason()));
        transferRepository.save(t);
        asset.setBranchId(toBranch);
        if (toLocation != null) asset.setLocation(toLocation);
        if (request.assignedTo() != null && !request.assignedTo().isBlank()) asset.setAssignedTo(request.assignedTo().trim());
        FixedAsset saved = assetRepository.save(asset);
        auditLogService.record("ASSET_TRANSFERRED", "Transferred asset " + saved.getCode() + " " + saved.getName() + " from "
                + names.get(fromBranch) + " to " + names.get(toBranch), "FixedAsset", saved.getId());
        return toDto(saved, names);
    }

    // ---- helpers ----

    private FixedAsset find(Long id) {
        FixedAsset asset = assetRepository.findById(id).orElseThrow(() -> ApiException.notFound("Asset not found: " + id));
        branchAccessService.assertCanAccess(asset.getBranchId());
        return asset;
    }

    AssetDto toDto(FixedAsset asset) {
        return toDto(asset, branchNames());
    }

    private AssetDto toDto(FixedAsset asset, Map<Long, String> names) {
        Long id = asset.getBranchId() != null ? asset.getBranchId() : branchService.headquartersId();
        return AssetDto.from(asset, asset.getLastDepreciatedThrough() != null, names.get(id));
    }

    private Map<Long, String> branchNames() {
        Map<Long, String> names = new HashMap<>();
        branchService.listAll().forEach(b -> names.put(b.id(), b.name()));
        return names;
    }

    private AssetCategory activeCategory(Long id) {
        AssetCategory category = categoryRepository.findById(id).orElseThrow(() -> ApiException.badRequest("Choose an asset category."));
        if (!category.isActive()) {
            throw ApiException.badRequest("The category " + category.getName() + " is not active.");
        }
        return category;
    }

    private void validateMoney(AssetRequest request) {
        if (request.cost().signum() <= 0) {
            throw ApiException.badRequest("Cost must be more than zero.");
        }
        if (request.salvageValue() != null && (request.salvageValue().signum() < 0 || request.salvageValue().compareTo(request.cost()) >= 0)) {
            throw ApiException.badRequest("Salvage value must be zero or more, and less than the cost.");
        }
        if (request.usefulLifeMonths() != null && request.usefulLifeMonths() < 1) {
            throw ApiException.badRequest("Useful life must be at least 1 month.");
        }
    }

    private void applyDetails(FixedAsset asset, AssetRequest request) {
        asset.setName(request.name().trim());
        asset.setCost(request.cost().setScale(2, RoundingMode.HALF_UP));
        asset.setPurchaseDate(request.purchaseDate());
        asset.setLocation(blankToNull(request.location()));
        asset.setAssignedTo(blankToNull(request.assignedTo()));
        asset.setSerialNumber(blankToNull(request.serialNumber()));
        asset.setSupplierName(blankToNull(request.supplierName()));
        asset.setNotes(blankToNull(request.notes()));
    }

    /** Method, life, salvage value and start date: what the request gives, otherwise the category's defaults. */
    private void applyDepreciationTerms(FixedAsset asset, AssetRequest request, AssetCategory category) {
        asset.setMethod(request.method() != null ? request.method() : category.getMethod());
        asset.setUsefulLifeMonths(request.usefulLifeMonths() != null ? request.usefulLifeMonths() : category.getUsefulLifeMonths());
        BigDecimal salvage = request.salvageValue() != null ? request.salvageValue()
                : asset.getCost().multiply(category.getSalvagePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        asset.setSalvageValue(salvage.setScale(2, RoundingMode.HALF_UP));
        asset.setDepreciationStartDate(request.depreciationStartDate() != null ? request.depreciationStartDate() : request.purchaseDate());
        asset.setProrateFirstMonth(request.prorateFirstMonth() != null ? request.prorateFirstMonth() : category.isProrateFirstMonth());
    }

    private String nextCode() {
        long n = assetRepository.count() + 1;
        String code = String.format("FA-%04d", n);
        while (assetRepository.existsByCode(code)) {
            n++;
            code = String.format("FA-%04d", n);
        }
        return code;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
