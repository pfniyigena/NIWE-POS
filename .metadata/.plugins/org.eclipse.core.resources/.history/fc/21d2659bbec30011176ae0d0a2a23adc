package com.posapp.warehouse;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.dto.StockTransferDto;
import com.posapp.warehouse.dto.StockTransferReceiveRequest;
import com.posapp.warehouse.dto.StockTransferRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Fourier: "multi-location/multi-warehouse tracking ... Stock Transfers", then later: "User can
 * make stock transfer to any location" — see {@link StockTransfer}'s own javadoc for the
 * PENDING/CONFIRMED/REJECTED shape this mirrors from {@code com.posapp.inventory.StockAdjustmentService},
 * and for why this moved from warehouse-to-warehouse to location-to-location (a transfer between
 * two locations under the *same* warehouse is now possible, which the original shape couldn't
 * express). Depends on both {@code CatalogService} (for the default-location side of a transfer —
 * see {@link #moveStock}) and {@code WarehouseService} (for everything else); neither of those
 * depends back on this class, so there's no risk of the circular-bean problem {@code
 * StockAdjustmentService}/{@code CatalogService} hit earlier.
 */
@Service
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final CatalogService catalogService;
    private final WarehouseService warehouseService;
    private final AuditLogService auditLogService;
    private final StoreSettingsService storeSettingsService;
    private final com.posapp.branch.BranchService branchService;
    /** Staff limited to certain branches can only confirm/reject transfers whose both ends are in their branches. */
    private final com.posapp.branch.BranchAccessService branchAccessService;
    /** Books a loss when an inter-branch delivery arrives short. */
    private final com.posapp.accounting.AccountingService accountingService;
    /** Batches and serial numbers travel with the stock. */
    private final com.posapp.tracking.TrackingService trackingService;

    public StockTransferService(StockTransferRepository stockTransferRepository,
                                 CatalogService catalogService,
                                 WarehouseService warehouseService,
                                 AuditLogService auditLogService,
                                 StoreSettingsService storeSettingsService,
                                 com.posapp.branch.BranchService branchService,
                                 com.posapp.branch.BranchAccessService branchAccessService,
                                 com.posapp.accounting.AccountingService accountingService,
                                 com.posapp.tracking.TrackingService trackingService) {
        this.trackingService = trackingService;
        this.accountingService = accountingService;
        this.stockTransferRepository = stockTransferRepository;
        this.catalogService = catalogService;
        this.warehouseService = warehouseService;
        this.auditLogService = auditLogService;
        this.storeSettingsService = storeSettingsService;
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
    }

    /** The warehouse's branch, or head office when it has none. */
    private com.posapp.branch.Branch branchOf(Warehouse warehouse) {
        return warehouse.getBranch() != null ? warehouse.getBranch() : branchService.getHeadquartersOrThrow();
    }

    @Transactional(readOnly = true)
    public List<StockTransferDto> listAll() {
        return stockTransferRepository.findAllByOrderByCreatedAtDesc().stream().map(StockTransferDto::from).toList();
    }

    /**
     * Just logs the claim — {@link StockTransferStatus#PENDING}, no real stock impact yet. Same
     * "preview, not yet real" reasoning as {@code StockAdjustmentService.create}: the negative-stock
     * check here is early feedback against the source location's stock *right now*, which
     * {@link #confirm} re-checks for real since it may have moved since this was logged.
     */
    @Transactional
    public StockTransferDto create(StockTransferRequest request) {
        if (request.quantity() <= 0) {
            throw ApiException.badRequest("Transfer quantity must be greater than zero");
        }
        if (request.fromLocationId().equals(request.toLocationId())) {
            throw ApiException.badRequest("Source and destination location must be different");
        }
        Product product = catalogService.getProductOrThrow(request.productId());
        WarehouseLocation fromLocation = getActiveLocationOrThrow(request.fromLocationId());
        WarehouseLocation toLocation = getActiveLocationOrThrow(request.toLocationId());

        int currentAtSource = warehouseService.getStockQuantity(product, fromLocation);
        if (currentAtSource - request.quantity() < 0 && !storeSettingsService.getSettings().allowNegativeStock()) {
            throw ApiException.badRequest("Cannot transfer " + request.quantity() + " of '" + product.getName() + "' out of '"
                    + fromLocation.getName() + "' — it only holds " + currentAtSource + "."
                    + " Enable \"Allow negative stock\" in Inventory settings to allow this.");
        }

        StockTransfer transfer = new StockTransfer();
        transfer.setProduct(product);
        transfer.setProductName(product.getName());
        transfer.setFromLocation(fromLocation);
        transfer.setFromLocationName(fromLocation.getName());
        transfer.setFromWarehouseName(fromLocation.getWarehouse().getName());
        transfer.setToLocation(toLocation);
        transfer.setToLocationName(toLocation.getName());
        transfer.setToWarehouseName(toLocation.getWarehouse().getName());
        // Inter-branch: snapshot each end's branch (a warehouse with none is head office's).
        com.posapp.branch.Branch fromBranch = branchOf(fromLocation.getWarehouse());
        com.posapp.branch.Branch toBranch = branchOf(toLocation.getWarehouse());
        transfer.setFromBranchId(fromBranch.getId());
        transfer.setFromBranchName(fromBranch.getName());
        transfer.setToBranchId(toBranch.getId());
        transfer.setToBranchName(toBranch.getName());
        transfer.setQuantity(request.quantity());
        transfer.setNote(request.note());
        transfer.setStatus(StockTransferStatus.PENDING);

        StockTransfer saved = stockTransferRepository.save(transfer);

        auditLogService.record("STOCK_TRANSFER_LOGGED",
                "Logged a transfer of " + request.quantity() + " x " + product.getName() + " from '" + describeLocation(fromLocation)
                        + "' to '" + describeLocation(toLocation) + "' — awaiting approval"
                        + (request.note() != null && !request.note().isBlank() ? ": " + request.note() : ""),
                "Product", product.getId());

        return StockTransferDto.from(saved);
    }

    /**
     * ADMIN or PERM_APPROVE_STOCK_TRANSFER (enforced in {@code StockTransferController}) —
     * re-checks the source location's *current* stock (it may have moved since this was logged)
     * and, if still valid, actually moves the quantity from {@code fromLocation} to {@code
     * toLocation}. See {@link #moveStock} for how each side is applied.
     */
    @Transactional
    public StockTransferDto confirm(Long id) {
        StockTransfer transfer = getOrThrow(id);
        boolean interBranch = isInterBranch(transfer);
        branchAccessService.assertCanAccess(transfer.getFromBranchId());
        // An inter-branch transfer is only dispatched here — the receiving branch confirms arrival later.
        if (!interBranch) {
            branchAccessService.assertCanAccess(transfer.getToBranchId());
        }
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw ApiException.badRequest("This transfer is already " + transfer.getStatus());
        }
        Product product = transfer.getProduct();
        WarehouseLocation fromLocation = transfer.getFromLocation();
        WarehouseLocation toLocation = transfer.getToLocation();
        if (product == null || fromLocation == null || toLocation == null) {
            throw ApiException.badRequest("Cannot confirm this transfer — its product or a location no longer exists");
        }

        int currentAtSource = warehouseService.getStockQuantity(product, fromLocation);
        if (currentAtSource - transfer.getQuantity() < 0 && !storeSettingsService.getSettings().allowNegativeStock()) {
            throw ApiException.badRequest("Cannot confirm this transfer — '" + fromLocation.getName() + "' only holds "
                    + currentAtSource + " of '" + product.getName() + "', and moving " + transfer.getQuantity()
                    + " would go negative. Enable \"Allow negative stock\" in Inventory settings to allow this.");
        }

        if (interBranch) {
            // Dispatch: the stock leaves the source now and sits "in transit" until the destination receives it.
            transfer.setFromStockBefore(currentAtSource);
            catalogService.adjustStockUntracked(product, -transfer.getQuantity(), fromLocation);
            transfer.setFromStockAfter(warehouseService.getStockQuantity(product, fromLocation));
            trackingService.dispatch(product, fromLocation, transfer.getQuantity(), transfer.getId());
            transfer.setStatus(StockTransferStatus.IN_TRANSIT);
            transfer.setConfirmedAt(Instant.now());
            transfer.setDispatchedAt(transfer.getConfirmedAt());
            StockTransfer dispatched = stockTransferRepository.save(transfer);
            auditLogService.record("STOCK_TRANSFER_DISPATCHED",
                    "Dispatched " + dispatched.getQuantity() + " x " + dispatched.getProductName() + " from '" + describeLocation(fromLocation)
                            + "' to '" + describeLocation(toLocation) + "' (" + dispatched.getToBranchName() + ") — in transit",
                    "Product", product.getId());
            return StockTransferDto.from(dispatched);
        }

        int destinationBefore = warehouseService.getStockQuantity(product, toLocation);
        moveStock(product, fromLocation, toLocation, transfer.getQuantity());
        transfer.setFromStockBefore(currentAtSource);
        transfer.setFromStockAfter(warehouseService.getStockQuantity(product, fromLocation));
        transfer.setToStockBefore(destinationBefore);
        transfer.setToStockAfter(warehouseService.getStockQuantity(product, toLocation));

        transfer.setStatus(StockTransferStatus.CONFIRMED);
        transfer.setConfirmedAt(Instant.now());
        StockTransfer saved = stockTransferRepository.save(transfer);

        auditLogService.record("STOCK_TRANSFER_CONFIRMED",
                "Confirmed transfer of " + saved.getQuantity() + " x " + saved.getProductName() + " from '"
                        + describeLocation(fromLocation) + "' to '" + describeLocation(toLocation) + "' — stock moved",
                "Product", product.getId());

        return StockTransferDto.from(saved);
    }

    /** ADMIN or PERM_APPROVE_STOCK_TRANSFER (enforced in {@code StockTransferController}) — discards a PENDING entry with no stock impact, e.g. logged in error. */
    @Transactional
    public StockTransferDto reject(Long id) {
        StockTransfer transfer = getOrThrow(id);
        branchAccessService.assertCanAccess(transfer.getFromBranchId());
        if (transfer.getStatus() == StockTransferStatus.IN_TRANSIT) {
            // Return to sender: the goods never arrived, so they go back to the source location.
            if (transfer.getProduct() != null && transfer.getFromLocation() != null) {
                transfer.setReturnStockBefore(warehouseService.getStockQuantity(transfer.getProduct(), transfer.getFromLocation()));
                catalogService.adjustStockUntracked(transfer.getProduct(), transfer.getQuantity(), transfer.getFromLocation());
                transfer.setReturnStockAfter(warehouseService.getStockQuantity(transfer.getProduct(), transfer.getFromLocation()));
                trackingService.returnToSender(transfer.getProduct(), transfer.getId(), transfer.getFromLocation());
            }
            transfer.setStatus(StockTransferStatus.REJECTED);
            StockTransfer returned = stockTransferRepository.save(transfer);
            auditLogService.record("STOCK_TRANSFER_RETURNED",
                    "Returned the in-transit transfer of " + returned.getQuantity() + " x " + returned.getProductName() + " to '"
                            + returned.getFromWarehouseName() + " / " + returned.getFromLocationName() + "'",
                    "Product", returned.getProduct() != null ? returned.getProduct().getId() : null);
            return StockTransferDto.from(returned);
        }
        branchAccessService.assertCanAccess(transfer.getToBranchId());
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw ApiException.badRequest("This transfer is already " + transfer.getStatus());
        }
        transfer.setStatus(StockTransferStatus.REJECTED);
        StockTransfer saved = stockTransferRepository.save(transfer);
        auditLogService.record("STOCK_TRANSFER_REJECTED",
                "Rejected the logged transfer of " + saved.getQuantity() + " x " + saved.getProductName() + " from '"
                        + saved.getFromLocationName() + "' to '" + saved.getToLocationName() + "'",
                "Product", saved.getProduct() != null ? saved.getProduct().getId() : null);
        return StockTransferDto.from(saved);
    }

    /**
     * Receiving end of an inter-branch transfer: puts the units that actually arrived into the destination
     * location. Fewer than were sent is allowed — the shortfall is booked as a stock loss (shrinkage) at the
     * product's cost, in the sending branch's books. More than were sent is not.
     */
    @Transactional
    public StockTransferDto receive(Long id, StockTransferReceiveRequest request) {
        StockTransfer transfer = getOrThrow(id);
        branchAccessService.assertCanAccess(transfer.getToBranchId());
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw ApiException.badRequest("Only an in-transit transfer can be received — this one is " + transfer.getStatus());
        }
        int received = request.getReceivedQuantity();
        if (received > transfer.getQuantity()) {
            throw ApiException.badRequest("Cannot receive more than was sent (" + transfer.getQuantity() + ")");
        }
        Product product = transfer.getProduct();
        WarehouseLocation toLocation = transfer.getToLocation();
        if (product == null || toLocation == null) {
            throw ApiException.badRequest("Cannot receive this transfer — its product or destination location no longer exists");
        }
        transfer.setToStockBefore(warehouseService.getStockQuantity(product, toLocation));
        if (received > 0) {
            catalogService.adjustStockUntracked(product, received, toLocation);
        }
        transfer.setToStockAfter(warehouseService.getStockQuantity(product, toLocation));
        trackingService.arrive(product, transfer.getId(), toLocation, received);
        int shortage = transfer.getQuantity() - received;
        transfer.setReceivedQuantity(received);
        transfer.setReceivedAt(Instant.now());
        transfer.setReceiveNote(request.getNote());
        transfer.setStatus(StockTransferStatus.RECEIVED);
        StockTransfer saved = stockTransferRepository.save(transfer);
        if (shortage > 0) {
            accountingService.postTransferShortage(saved, shortage);
        }
        auditLogService.record("STOCK_TRANSFER_RECEIVED",
                "Received " + received + " of " + saved.getQuantity() + " x " + saved.getProductName() + " at '" + describeLocation(toLocation) + "'"
                        + (shortage > 0 ? " — " + shortage + " short" + (request.getNote() != null && !request.getNote().isBlank() ? " (" + request.getNote() + ")" : "") : ""),
                "Product", product.getId());
        return StockTransferDto.from(saved);
    }

    private static boolean isInterBranch(StockTransfer t) {
        return t.getFromBranchId() != null && t.getToBranchId() != null && !t.getFromBranchId().equals(t.getToBranchId());
    }

    /**
     * Applies the two sides of a confirmed transfer, through {@code CatalogService#adjustStock}'s
     * location-granular overload — whichever side IS the primary warehouse's own canonical SALES
     * location keeps {@code Product.stockQuantity} (and the low-stock alert) in sync exactly like a
     * register-driven sale/return does (see {@code CatalogService.adjustStock(Product, int,
     * WarehouseLocation)}'s own javadoc); any other location (including a RECEIVING, STORAGE, or
     * DAMAGED one — see {@code LocationType}'s own javadoc) adjusts only that location's row. This
     * is also what makes a Stock Transfer the only way to move received stock out of RECEIVING onto
     * a sellable location, or stock out of DAMAGED back into circulation. {@code fromLocation !=
     * toLocation} is already enforced in {@link #create}.
     */
    private void moveStock(Product product, WarehouseLocation fromLocation, WarehouseLocation toLocation, int quantity) {
        catalogService.adjustStockUntracked(product, -quantity, fromLocation);
        catalogService.adjustStockUntracked(product, quantity, toLocation);
        trackingService.moveNow(product, fromLocation, toLocation, quantity);
    }

    private WarehouseLocation getActiveLocationOrThrow(Long id) {
        WarehouseLocation location = warehouseService.getLocationOrThrow(id);
        if (!location.isActive()) {
            throw ApiException.badRequest("Location '" + location.getName() + "' is inactive");
        }
        return location;
    }

    private String describeLocation(WarehouseLocation location) {
        return location.getWarehouse().getName() + " / " + location.getName();
    }

    private StockTransfer getOrThrow(Long id) {
        return stockTransferRepository.findById(id).orElseThrow(() -> ApiException.notFound("Stock transfer not found: " + id));
    }
}
