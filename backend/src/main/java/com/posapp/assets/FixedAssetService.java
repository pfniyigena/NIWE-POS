package com.posapp.assets;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.accounting.AccountType;
import com.posapp.accounting.AccountingService;
import com.posapp.accounting.AccountingService.AssetAccounts;
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
    private final AccountRepository accountRepository;
    private final AssetHistoryService history;
    private final AssetCustodyService custodyService;
    private final AssetSettingsService settingsService;
    private final AssetAttachmentRepository attachmentRepository;

    public FixedAssetService(AssetCategoryRepository categoryRepository, FixedAssetRepository assetRepository,
                             DepreciationEntryRepository entryRepository, AssetDepreciationService depreciationService,
                             AccountingService accountingService, AuditLogService auditLogService, BranchService branchService,
                             BranchAccessService branchAccessService, AssetTransferRepository transferRepository,
                             AccountRepository accountRepository, AssetHistoryService history, AssetCustodyService custodyService,
                             AssetSettingsService settingsService, AssetAttachmentRepository attachmentRepository) {
        this.accountRepository = accountRepository;
        this.history = history;
        this.custodyService = custodyService;
        this.settingsService = settingsService;
        this.attachmentRepository = attachmentRepository;
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
        Account assetAccount = pickAccount(request.assetAccountId(), AccountType.ASSET, "asset");
        Account accumulated = pickAccount(request.accumulatedAccountId(), AccountType.ASSET, "accumulated depreciation");
        Account expense = pickAccount(request.expenseAccountId(), AccountType.EXPENSE, "depreciation expense");
        boolean changed = !sameAccount(category.getAssetAccount(), assetAccount) || !sameAccount(category.getAccumulatedAccount(), accumulated)
                || !sameAccount(category.getExpenseAccount(), expense);
        if (changed && category.getId() != null && assetRepository.countByCategoryId(category.getId()) > 0) {
            throw ApiException.conflict("The accounts of a category can only be changed while it has no assets, because their postings are already in the books.");
        }
        category.setAssetAccount(assetAccount);
        category.setAccumulatedAccount(accumulated);
        category.setExpenseAccount(expense);
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    private Account pickAccount(Long id, AccountType type, String what) {
        if (id == null) return null;
        Account a = accountRepository.findById(id).orElseThrow(() -> ApiException.badRequest("The " + what + " account was not found."));
        if (a.getType() != type || !a.isActive()) {
            throw ApiException.badRequest("The " + what + " account must be an active " + type.name().toLowerCase() + " account.");
        }
        return a;
    }

    private static boolean sameAccount(Account a, Account b) {
        return (a == null && b == null) || (a != null && b != null && a.getId().equals(b.getId()));
    }

    /** The accounts postings for this category use (the standard ones unless the category has its own). */
    public AssetAccounts accountsFor(Long categoryId) {
        return categoryRepository.findById(categoryId).map(AssetDepreciationService::accountsOf).orElse(AssetAccounts.STANDARD);
    }

    // ---- assets ----

    /** What the register can be filtered on. Every field is optional. */
    public record Filter(AssetStatus status, Long branchId, Long categoryId, String q, String location, String custodian, String view) {
        public static Filter of(AssetStatus status, Long branchId) {
            return new Filter(status, branchId, null, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public List<AssetDto> list(AssetStatus status, Long branchId) {
        return list(Filter.of(status, branchId));
    }

    @Transactional(readOnly = true)
    public List<AssetDto> list(Filter filter) {
        Map<Long, String> names = branchNames();
        Map<Long, Long> files = fileCounts();
        AssetSettings settings = settingsService.entity();
        LocalDate today = LocalDate.now();
        List<AssetDto> out = new java.util.ArrayList<>();
        for (FixedAsset a : visibleAssets(filter.status(), filter.branchId())) {
            if (!matches(a, filter, settings, today)) continue;
            out.add(toDto(a, names, files.getOrDefault(a.getId(), 0L), settings, today));
        }
        String view = filter.view() == null ? "" : filter.view();
        if (view.equals("soon")) {
            out.removeIf(d -> d.monthsLeft() == null || d.monthsLeft() > settings.getSoonMonths());
        }
        return out;
    }

    /** The register's filters in one place, so the list, the Excel export and the bulk actions agree. */
    public boolean matches(FixedAsset a, Filter f, AssetSettings settings, LocalDate today) {
        if (f.categoryId() != null && !f.categoryId().equals(a.getCategory().getId())) return false;
        if (f.location() != null && !f.location().isBlank() && (a.getLocation() == null || !a.getLocation().toLowerCase().contains(f.location().trim().toLowerCase()))) return false;
        if (f.custodian() != null && !f.custodian().isBlank() && (a.getCustodianName() == null || !a.getCustodianName().toLowerCase().contains(f.custodian().trim().toLowerCase()))) return false;
        if (f.q() != null && !f.q().isBlank()) {
            String needle = f.q().trim().toLowerCase();
            boolean hit = false;
            for (String v : new String[]{a.getCode(), a.getName(), a.getSerialNumber(), a.getLocation(), a.getCustodianName(), a.getSupplierName(), a.getPurchaseOrderNumber(), a.getInsurancePolicy()}) {
                if (v != null && v.toLowerCase().contains(needle)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) return false;
        }
        String view = f.view() == null ? "" : f.view();
        boolean open = a.getStatus() != AssetStatus.DISPOSED;
        return switch (view) {
            case "no-custodian" -> open && a.getCustodianName() == null;
            case "held" -> open && a.getCustodianName() != null;
            case "warranty-expiring" -> open && "EXPIRING".equals(state(a.getWarrantyExpiresOn(), settings.getAlertDays(), today));
            case "insurance-expiring" -> open && "EXPIRING".equals(state(a.getInsuranceExpiresOn(), settings.getAlertDays(), today));
            case "expired" -> open && ("EXPIRED".equals(state(a.getWarrantyExpiresOn(), settings.getAlertDays(), today))
                    || "EXPIRED".equals(state(a.getInsuranceExpiresOn(), settings.getAlertDays(), today)));
            default -> true;
        };
    }

    public static String state(LocalDate expires, int alertDays, LocalDate today) {
        if (expires == null) return null;
        if (expires.isBefore(today)) return "EXPIRED";
        if (!expires.isAfter(today.plusDays(alertDays))) return "EXPIRING";
        return "OK";
    }

    private Map<Long, Long> fileCounts() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] row : attachmentRepository.countsByAsset()) {
            m.put((Long) row[0], (Long) row[1]);
        }
        return m;
    }

    /** Assets of one status (or all), optionally of one branch, limited to the branches the signed-in user may work with. */
    public List<FixedAsset> visibleAssets(AssetStatus status, Long branchId) {
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
        asset.setOriginalCost(asset.getCost());
        FixedAsset saved = persistNew(asset, link != null ? AcquisitionFunding.NONE : request.acquisitionFunding());
        history.event(saved.getId(), "REGISTERED", "Registered at cost " + saved.getCost() + (link != null ? " from purchase order " + link.orderNumber() : ""));
        if (request.assignedTo() != null && !request.assignedTo().isBlank()) {
            LocalDate on = request.purchaseDate().isAfter(LocalDate.now()) ? LocalDate.now() : request.purchaseDate();
            custodyService.checkOut(saved, null, request.assignedTo(), on, null, null);
            saved = assetRepository.save(saved);
        }
        return saved;
    }

    private FixedAsset persistNew(FixedAsset asset, AcquisitionFunding funding) {
        FixedAsset saved = assetRepository.save(asset);
        AcquisitionFunding how = funding != null ? funding : AcquisitionFunding.NONE;
        if (how != AcquisitionFunding.NONE) {
            accountingService.postAssetAcquisition(saved.getId(), "Asset purchase " + saved.getCode() + " " + saved.getName(),
                    saved.getCost(), how == AcquisitionFunding.CASH, saved.getBranchId(), AssetDepreciationService.accountsOf(saved.getCategory()));
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
            asset.setOriginalCost(asset.getCost());
            asset.setMonthlyCharge(null);
            asset.setRateLifeMonths(null);
        } else if (asset.getCost().compareTo(request.cost()) != 0 || !asset.getPurchaseDate().equals(request.purchaseDate())) {
            throw ApiException.conflict("Cost and purchase date can't be changed once depreciation or the purchase has been posted to Accounting.");
        }
        custodyService.applyAssignment(asset, request.assignedTo(), null);
        FixedAsset saved = assetRepository.save(asset);
        history.event(saved.getId(), "UPDATED", "Details changed");
        auditLogService.record("ASSET_UPDATED", "Updated asset " + saved.getCode() + " " + saved.getName(), "FixedAsset", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        FixedAsset asset = find(id);
        if (asset.getLastDepreciatedThrough() != null || asset.isAcquisitionPosted() || asset.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.conflict("This asset has accounting history (depreciation, purchase posting or disposal) and can't be deleted.");
        }
        deleteDependents(id);
        assetRepository.delete(asset);
        auditLogService.record("ASSET_DELETED", "Deleted asset " + asset.getCode() + " " + asset.getName(), "FixedAsset", id);
    }

    private void deleteDependents(Long id) {
        attachmentRepository.deleteAll(attachmentRepository.findByAssetIdOrderByIdAsc(id));
        custodyService.deleteAllOf(id);
        history.deleteAllOf(id);
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
        BigDecimal limit = settingsService.entity().getDisposalApprovalLimit();
        if (limit.signum() > 0 && bookValue.compareTo(limit) > 0 && !AssetHistoryService.actor().disposalApprover()) {
            throw ApiException.forbidden("This asset is worth " + bookValue + ", above the disposal limit of " + limit + ". Ask someone with the 'Approve large asset disposals' permission to do it.");
        }
        asset.setDisposalDate(date);
        asset.setDisposalProceeds(proceeds);
        asset.setDisposalGainLoss(proceeds.subtract(bookValue));
        asset.setDisposalNote(request.note());
        asset.setStatus(AssetStatus.DISPOSED);
        FixedAsset saved = assetRepository.save(asset);
        accountingService.postAssetDisposal(saved.getId(), "Disposal of asset " + saved.getCode() + " " + saved.getName(),
                saved.getCost(), saved.getAccumulatedDepreciation(), proceeds, saved.getBranchId(),
                AssetDepreciationService.accountsOf(saved.getCategory()), false);
        custodyReleaseOnDisposal(saved, date);
        history.event(saved.getId(), "DISPOSED", (proceeds.signum() > 0 ? "Sold for " + proceeds : "Written off") + ", gain/loss " + saved.getDisposalGainLoss()
                + (request.note() != null && !request.note().isBlank() ? " - " + request.note().trim() : ""));
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
        if (request.assignedTo() != null && !request.assignedTo().isBlank()) custodyService.applyAssignment(asset, request.assignedTo(), date);
        FixedAsset saved = assetRepository.save(asset);
        history.event(saved.getId(), "TRANSFER", "Moved from " + names.get(fromBranch) + (t.getFromLocation() != null ? " / " + t.getFromLocation() : "")
                + " to " + names.get(toBranch) + (t.getToLocation() != null ? " / " + t.getToLocation() : "")
                + (t.getReason() != null ? " - " + t.getReason() : ""));
        auditLogService.record("ASSET_TRANSFERRED", "Transferred asset " + saved.getCode() + " " + saved.getName() + " from "
                + names.get(fromBranch) + " to " + names.get(toBranch), "FixedAsset", saved.getId());
        return toDto(saved, names);
    }

    /** A disposed asset is no longer held by anyone: the open spell ends on the disposal date. */
    private void custodyReleaseOnDisposal(FixedAsset asset, LocalDate date) {
        if (asset.getCustodianName() != null) {
            LocalDate on = date.isBefore(asset.getCheckedOutOn() != null ? asset.getCheckedOutOn() : date) ? asset.getCheckedOutOn() : date;
            custodyService.checkIn(asset, on, "Disposed", null);
            assetRepository.save(asset);
        }
    }

    // ---- helpers ----

    /** The entity, with the branch access check (for the handover sheet). */
    FixedAsset entity(Long id) {
        return find(id);
    }

    private FixedAsset find(Long id) {
        FixedAsset asset = assetRepository.findById(id).orElseThrow(() -> ApiException.notFound("Asset not found: " + id));
        branchAccessService.assertCanAccess(asset.getBranchId());
        return asset;
    }

    AssetDto toDto(FixedAsset asset) {
        return toDto(asset, branchNames(), attachmentRepository.countByAssetId(asset.getId()), settingsService.entity(), LocalDate.now());
    }

    private AssetDto toDto(FixedAsset asset, Map<Long, String> names) {
        return toDto(asset, names, attachmentRepository.countByAssetId(asset.getId()), settingsService.entity(), LocalDate.now());
    }

    private AssetDto toDto(FixedAsset asset, Map<Long, String> names, long fileCount, AssetSettings settings, LocalDate today) {
        Long id = asset.getBranchId() != null ? asset.getBranchId() : branchService.headquartersId();
        boolean open = asset.getStatus() != AssetStatus.DISPOSED;
        List<BigDecimal> projection = asset.getStatus() == AssetStatus.ACTIVE ? depreciationService.project(asset, 600) : List.of();
        return AssetDto.from(asset, asset.getLastDepreciatedThrough() != null, names.get(id), fileCount,
                open ? state(asset.getWarrantyExpiresOn(), settings.getAlertDays(), today) : null,
                open ? state(asset.getInsuranceExpiresOn(), settings.getAlertDays(), today) : null,
                projection.isEmpty() ? null : projection.get(0), projection.isEmpty() ? null : projection.size());
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
        asset.setWarrantyExpiresOn(request.warrantyExpiresOn());
        asset.setInsuranceExpiresOn(request.insuranceExpiresOn());
        asset.setInsurancePolicy(blankToNull(request.insurancePolicy()));
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
