package com.posapp.inventory;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.common.ApiException;
import com.posapp.inventory.dto.ProcessPurchaseReturnRequest;
import com.posapp.inventory.dto.PurchaseReturnDto;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory
 * Module." Mirrors {@code com.posapp.salesorder.SalesOrderReturnService} scoped to a Purchase
 * Order instead of a Sales Order — see {@link PurchaseReturn}'s class javadoc for the
 * money-handling choice (informational only, no payout). Depends on {@link PurchaseOrderRepository}
 * directly rather than {@link PurchaseOrderService} (whose own order lookup is private) — same
 * "read the domain's repository directly" convention {@code CustomerCreditService}'s javadoc
 * documents elsewhere in this codebase.
 *
 * <p>Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — a return reverses exactly the
 * stock {@link PurchaseOrderService#receiveLines} moved onto the warehouse's RECEIVING location on
 * receipt, so it must reverse it on that same location, not on {@code Product.stockQuantity}
 * directly. Multi-warehouse phase 2: that is the purchase order's own receiving warehouse
 * (primary when none was chosen).
 */
@Service
public class PurchaseReturnService {

    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseReturnLineRepository purchaseReturnLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final AccountingService accountingService;
    private final WarehouseService warehouseService;

    public PurchaseReturnService(PurchaseReturnRepository purchaseReturnRepository,
                                  PurchaseReturnLineRepository purchaseReturnLineRepository,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  CatalogService catalogService,
                                  AuditLogService auditLogService,
                                  AccountingService accountingService,
                                  WarehouseService warehouseService) {
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.purchaseReturnLineRepository = purchaseReturnLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
    }

    @Transactional(readOnly = true)
    public List<PurchaseReturnDto> listAll() {
        return purchaseReturnRepository.findAllByOrderByReturnDateDesc().stream().map(PurchaseReturnDto::from).toList();
    }

    /** Task 4's own return history — the Purchase Order detail screen's return history section. */
    @Transactional(readOnly = true)
    public List<PurchaseReturnDto> listForOrder(Long purchaseOrderId) {
        return purchaseReturnRepository.findByPurchaseOrderIdOrderByReturnDateDesc(purchaseOrderId).stream()
                .map(PurchaseReturnDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseReturnDto getById(Long id) {
        return PurchaseReturnDto.from(getEntityOrThrow(id));
    }

    /**
     * Every Purchase Order line id on this order that has ever had anything returned, mapped to
     * the total quantity returned — backs the "already returned" figure on the order detail
     * screen's "process a return" panel.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> returnedQuantitiesForOrder(Long purchaseOrderId) {
        return purchaseReturnLineRepository.findByPurchaseOrderId(purchaseOrderId).stream()
                .collect(Collectors.groupingBy(l -> l.getOrderLine().getId(), Collectors.summingInt(PurchaseReturnLine::getQuantity)));
    }

    /**
     * Only the *received* portion of a line can be returned — nothing was ever physically in the
     * store beyond what actually arrived. Reverses stock for exactly the quantity being returned
     * (-qty, since it's leaving the store back to the supplier — the opposite direction {@link
     * PurchaseOrderService#receiveLines} moves stock) and never touches the Purchase Order's own
     * status — a return is a side-ledger, same as the Sales Order return leaves the original
     * order's own status alone. The order itself must be SENT/PARTIALLY_RECEIVED/RECEIVED — i.e.
     * it must have actually had something arrive before any of it can be returned.
     */
    @Transactional
    public PurchaseReturnDto processReturn(ProcessPurchaseReturnRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> ApiException.notFound("Purchase order not found: " + request.getPurchaseOrderId()));
        if (po.getStatus() != PurchaseOrderStatus.SENT
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED
                && po.getStatus() != PurchaseOrderStatus.RECEIVED) {
            throw ApiException.badRequest("Cannot process a return against purchase order " + po.getOrderNumber() + " — it is " + po.getStatus());
        }

        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.setPurchaseOrder(po);
        purchaseReturn.setReason(request.getReason());
        // Own "PO-RET-" + millis shape — see PurchaseReturn.returnNumber's javadoc.
        purchaseReturn.setReturnNumber("PO-RET-" + System.currentTimeMillis());

        // Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — resolved once for the
        // whole call; every line reversed against this return comes back out of here, mirroring
        // where PurchaseOrderService#receiveLines originally put it.
        // Multi-warehouse phase 2: the PO's own receiving warehouse (primary when none was chosen).
        Warehouse receivingWarehouse = po.getReceivingWarehouse() != null ? po.getReceivingWarehouse() : warehouseService.getDefaultWarehouseOrThrow();
        WarehouseLocation receivingLocation = warehouseService.getReceivingLocationOrThrow(receivingWarehouse);

        BigDecimal refundTotal = BigDecimal.ZERO;
        StringBuilder returnedDescription = new StringBuilder();
        for (ProcessPurchaseReturnRequest.ReturnLineEntry entry : request.getLines()) {
            PurchaseOrderLine line = po.getLines().stream()
                    .filter(l -> l.getId().equals(entry.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Line " + entry.getLineId() + " does not belong to purchase order " + po.getOrderNumber()));

            int alreadyReturned = purchaseReturnLineRepository.sumReturnedQuantityForLine(line.getId());
            int remaining = line.getQuantityReceived() - alreadyReturned;
            if (entry.getQuantity() > remaining) {
                throw ApiException.badRequest("Cannot return " + entry.getQuantity() + " of '" + line.getProductName()
                        + "' — only " + remaining + " remaining (of " + line.getQuantityReceived() + " received, " + alreadyReturned + " already returned)");
            }

            BigDecimal lineRefund = line.getUnitCost()
                    .multiply(BigDecimal.valueOf(entry.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            refundTotal = refundTotal.add(lineRefund);

            PurchaseReturnLine returnLine = new PurchaseReturnLine();
            returnLine.setPurchaseReturn(purchaseReturn);
            returnLine.setOrderLine(line);
            returnLine.setQuantity(entry.getQuantity());
            returnLine.setRefundAmount(lineRefund);
            purchaseReturn.getLines().add(returnLine);

            if (line.getProduct() != null) {
                // Task 6: "Stock Movement History" — snapshot before/after, see PurchaseReturnLine's
                // javadoc. Reads/writes the RECEIVING location's own quantity, not
                // Product.stockQuantity directly — see this class's own javadoc.
                int stockBefore = warehouseService.getStockQuantity(line.getProduct(), receivingLocation);
                catalogService.adjustStock(line.getProduct(), -entry.getQuantity(), receivingLocation);
                returnLine.setStockBefore(stockBefore);
                returnLine.setStockAfter(warehouseService.getStockQuantity(line.getProduct(), receivingLocation));
            }
            if (returnedDescription.length() > 0) returnedDescription.append(", ");
            returnedDescription.append(entry.getQuantity()).append(" x ").append(line.getProductName());
        }
        purchaseReturn.setRefundTotal(refundTotal);

        PurchaseReturn saved = purchaseReturnRepository.save(purchaseReturn);
        auditLogService.record("PURCHASE_RETURN_PROCESSED",
                "Processed return " + saved.getReturnNumber() + " (" + returnedDescription + ") against purchase order "
                        + po.getOrderNumber() + " for " + (po.getSupplier() != null ? po.getSupplier().getName() : "—")
                        + " (refund due from supplier " + refundTotal + ")",
                "PurchaseReturn", saved.getId());

        // Fourier: "all...Inventory...module will be integrated when accounting is enabled" — see
        // AccountingService.postPurchaseReturn's own javadoc.
        accountingService.postPurchaseReturn(saved, refundTotal);

        return PurchaseReturnDto.from(saved);
    }

    private PurchaseReturn getEntityOrThrow(Long id) {
        return purchaseReturnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Purchase return not found: " + id));
    }
}
