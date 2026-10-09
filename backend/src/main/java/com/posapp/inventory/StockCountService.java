package com.posapp.inventory;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.branch.BranchService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Category;
import com.posapp.catalog.CategoryRepository;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.inventory.dto.StockAdjustmentDto;
import com.posapp.inventory.dto.StockAdjustmentRequest;
import com.posapp.inventory.dto.StockCountAddLineRequest;
import com.posapp.inventory.dto.StockCountDto;
import com.posapp.inventory.dto.StockCountRequest;
import com.posapp.inventory.dto.StockCountSaveRequest;
import com.posapp.warehouse.ProductStock;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Physical stock counts. Starting a count freezes every tracked product's system quantity at each active
 * location of the chosen warehouse onto a sheet; quantities are entered while sales carry on; the
 * count is then submitted and approved. Approving posts each counted difference (counted minus the
 * quantity frozen at the start) through {@link StockAdjustmentService} with reason RECOUNT — so the stock
 * change, its audit trail and its accounting impact are exactly those of any other adjustment, and
 * anything sold or received after the sheet was started is preserved because the change is relative.
 */
@Service
public class StockCountService {

    private final StockCountRepository repository;
    private final WarehouseService warehouseService;
    private final CatalogService catalogService;
    private final CategoryRepository categoryRepository;
    private final StockAdjustmentService stockAdjustmentService;
    private final AuditLogService auditLogService;
    private final BranchService branchService;
    private final BranchAccessService branchAccessService;

    public StockCountService(StockCountRepository repository,
                             WarehouseService warehouseService,
                             CatalogService catalogService,
                             CategoryRepository categoryRepository,
                             StockAdjustmentService stockAdjustmentService,
                             AuditLogService auditLogService,
                             BranchService branchService,
                             BranchAccessService branchAccessService) {
        this.repository = repository;
        this.warehouseService = warehouseService;
        this.catalogService = catalogService;
        this.categoryRepository = categoryRepository;
        this.stockAdjustmentService = stockAdjustmentService;
        this.auditLogService = auditLogService;
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<StockCountDto> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> branchAccessService.canAccess(c.getBranchId()))
                .map(c -> StockCountDto.from(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public StockCountDto getById(Long id) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        return StockCountDto.from(count, true);
    }

    /** Generates the sheet: every tracked, active, non-service product holding (or owing) stock at an active location of the warehouse. */
    @Transactional
    public StockCountDto start(StockCountRequest request) {
        Warehouse warehouse = warehouseService.getEntityOrThrow(request.getWarehouseId());
        if (!warehouse.isActive()) {
            throw ApiException.badRequest("Warehouse '" + warehouse.getName() + "' is inactive and cannot be counted");
        }
        Long branchId = warehouseService.branchIdOf(warehouse);
        branchAccessService.assertCanAccess(branchId);

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> ApiException.notFound("Category not found: " + request.getCategoryId()));
        }

        StockCount count = new StockCount();
        count.setWarehouse(warehouse);
        count.setWarehouseName(warehouse.getName());
        count.setCategory(category);
        count.setNotes(request.getNotes());
        count.setBranchId(branchId);
        count.setCountNumber(branchService.prefixedNumber(branchId, "SC-" + System.currentTimeMillis()));

        for (WarehouseLocation location : warehouseService.activeLocations(warehouse)) {
            for (ProductStock row : warehouseService.stockRowsAt(location)) {
                Product product = row.getProduct();
                if (row.getQuantity() == 0 || product == null || !product.isActive() || product.isService() || !product.isTrackStock()) continue;
                if (category != null && (product.getCategory() == null || !category.getId().equals(product.getCategory().getId()))) continue;
                count.getLines().add(newLine(count, product, location, row.getQuantity()));
            }
        }
        if (count.getLines().isEmpty()) {
            throw ApiException.badRequest("Nothing to count — no tracked product holds stock in '" + warehouse.getName() + "'"
                    + (category != null ? " for category '" + category.getName() + "'" : "")
                    + ". Add products to the count after starting it, or pick another warehouse.");
        }
        StockCount saved = repository.save(count);
        auditLogService.record("STOCK_COUNT_STARTED",
                "Started stock count " + saved.getCountNumber() + " of " + warehouse.getName() + " (" + saved.getLines().size() + " lines)",
                "StockCount", saved.getId());
        return StockCountDto.from(saved, true);
    }

    @Transactional
    public StockCountDto saveCounts(Long id, StockCountSaveRequest request) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        requireStatus(count, StockCountStatus.IN_PROGRESS, "edited");
        if (request.getLines() != null) {
            for (StockCountSaveRequest.Entry entry : request.getLines()) {
                StockCountLine line = lineOrThrow(count, entry.getLineId());
                if (entry.getCountedQuantity() != null && entry.getCountedQuantity() < 0) {
                    throw ApiException.badRequest("Counted quantity for '" + line.getProductName() + "' cannot be negative");
                }
                line.setCountedQuantity(entry.getCountedQuantity());
            }
        }
        return StockCountDto.from(repository.save(count), true);
    }

    @Transactional
    public StockCountDto addLine(Long id, StockCountAddLineRequest request) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        requireStatus(count, StockCountStatus.IN_PROGRESS, "edited");
        Product product = catalogService.getProductOrThrow(request.getProductId());
        if (product.isService() || !product.isTrackStock()) {
            throw ApiException.badRequest("'" + product.getName() + "' doesn't track stock, so it can't be counted");
        }
        WarehouseLocation location = warehouseService.getLocationOrThrow(request.getLocationId());
        if (location.getWarehouse() == null || !location.getWarehouse().getId().equals(count.getWarehouse().getId())) {
            throw ApiException.badRequest("That location isn't in warehouse '" + count.getWarehouseName() + "'");
        }
        for (StockCountLine existing : count.getLines()) {
            if (existing.getProduct() != null && existing.getProduct().getId().equals(product.getId())
                    && existing.getLocation() != null && existing.getLocation().getId().equals(location.getId())) {
                throw ApiException.badRequest("'" + product.getName() + "' at " + location.getName() + " is already on this count");
            }
        }
        count.getLines().add(newLine(count, product, location, warehouseService.getStockQuantity(product, location)));
        return StockCountDto.from(repository.save(count), true);
    }

    @Transactional
    public StockCountDto removeLine(Long id, Long lineId) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        requireStatus(count, StockCountStatus.IN_PROGRESS, "edited");
        StockCountLine line = lineOrThrow(count, lineId);
        count.getLines().remove(line);
        return StockCountDto.from(repository.save(count), true);
    }

    /** Counting finished — locks the sheet and sends it for sign-off. */
    @Transactional
    public StockCountDto submit(Long id) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        requireStatus(count, StockCountStatus.IN_PROGRESS, "submitted");
        if (count.getLines().stream().noneMatch(l -> l.getCountedQuantity() != null)) {
            throw ApiException.badRequest("Enter at least one counted quantity before submitting");
        }
        count.setStatus(StockCountStatus.SUBMITTED);
        count.setSubmittedAt(Instant.now());
        StockCount saved = repository.save(count);
        auditLogService.record("STOCK_COUNT_SUBMITTED", "Submitted stock count " + saved.getCountNumber() + " for approval", "StockCount", saved.getId());
        return StockCountDto.from(saved, true);
    }

    /** Sends a submitted count back for more counting. */
    @Transactional
    public StockCountDto reopen(Long id) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        requireStatus(count, StockCountStatus.SUBMITTED, "reopened");
        count.setStatus(StockCountStatus.IN_PROGRESS);
        count.setSubmittedAt(null);
        StockCount saved = repository.save(count);
        auditLogService.record("STOCK_COUNT_REOPENED", "Reopened stock count " + saved.getCountNumber() + " for more counting", "StockCount", saved.getId());
        return StockCountDto.from(saved, true);
    }

    /**
     * Posts every counted difference as a RECOUNT stock adjustment and confirms it. All-or-nothing: if any one
     * adjustment is refused (e.g. it would push stock negative while "Allow negative stock" is off) the whole
     * approval is rolled back and the count stays submitted.
     */
    @Transactional
    public StockCountDto approve(Long id) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        requireStatus(count, StockCountStatus.SUBMITTED, "approved");

        int posted = 0;
        for (StockCountLine line : count.getLines()) {
            if (line.getCountedQuantity() == null) continue;
            int variance = line.getCountedQuantity() - line.getSystemQuantity();
            if (variance == 0) continue;
            StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
            adjustment.setProductId(line.getProduct().getId());
            adjustment.setLocationId(line.getLocation().getId());
            adjustment.setQuantityDelta(variance);
            adjustment.setReason(StockAdjustmentReason.RECOUNT.name());
            adjustment.setNote("Stock count " + count.getCountNumber() + ": counted " + line.getCountedQuantity() + ", system had " + line.getSystemQuantity());
            StockAdjustmentDto created = stockAdjustmentService.create(adjustment);
            stockAdjustmentService.confirm(created.id());
            line.setAdjustmentId(created.id());
            posted++;
        }
        count.setStatus(StockCountStatus.APPROVED);
        count.setApprovedAt(Instant.now());
        count.setApprovedBy(currentUsername());
        StockCount saved = repository.save(count);
        auditLogService.record("STOCK_COUNT_APPROVED",
                "Approved stock count " + saved.getCountNumber() + " — " + posted + " adjustment" + (posted == 1 ? "" : "s") + " posted",
                "StockCount", saved.getId());
        return StockCountDto.from(saved, true);
    }

    @Transactional
    public StockCountDto cancel(Long id) {
        StockCount count = getOrThrow(id);
        branchAccessService.assertCanAccess(count.getBranchId());
        if (count.getStatus() != StockCountStatus.IN_PROGRESS && count.getStatus() != StockCountStatus.SUBMITTED) {
            throw ApiException.badRequest("Stock count " + count.getCountNumber() + " is " + count.getStatus() + " and can no longer be cancelled");
        }
        count.setStatus(StockCountStatus.CANCELLED);
        StockCount saved = repository.save(count);
        auditLogService.record("STOCK_COUNT_CANCELLED", "Cancelled stock count " + saved.getCountNumber() + " — nothing was posted", "StockCount", saved.getId());
        return StockCountDto.from(saved, true);
    }

    private StockCountLine newLine(StockCount count, Product product, WarehouseLocation location, int systemQuantity) {
        StockCountLine line = new StockCountLine();
        line.setStockCount(count);
        line.setProduct(product);
        line.setSku(product.getSku());
        line.setProductName(product.getName());
        line.setLocation(location);
        line.setLocationName(location.getName());
        line.setSystemQuantity(systemQuantity);
        line.setUnitCost(product.getCostPrice());
        return line;
    }

    private StockCountLine lineOrThrow(StockCount count, Long lineId) {
        for (StockCountLine line : count.getLines()) {
            if (line.getId() != null && line.getId().equals(lineId)) return line;
        }
        throw ApiException.badRequest("Line " + lineId + " does not belong to stock count " + count.getCountNumber());
    }

    private void requireStatus(StockCount count, StockCountStatus expected, String verb) {
        if (count.getStatus() != expected) {
            throw ApiException.badRequest("Stock count " + count.getCountNumber() + " is " + count.getStatus() + " and cannot be " + verb);
        }
    }

    private StockCount getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Stock count not found: " + id));
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
