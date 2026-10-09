package com.posapp.salesorder;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.common.ApiException;
import com.posapp.salesorder.dto.ProcessSalesOrderReturnRequest;
import com.posapp.salesorder.dto.SalesOrderReturnDto;
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
 * Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module." Mirrors
 * {@code com.posapp.returns.ReturnsService.processReturn} scoped to a Sales Order instead of a
 * completed POS sale — see {@link SalesOrderReturn}'s class javadoc for the money-handling
 * difference (informational only, no refund method/payout). Reaches {@link SalesOrderService}
 * only for its package-private {@link SalesOrderService#getEntityOrThrow} rather than duplicating
 * that lookup — safe: {@code SalesOrderService} never depends back on this class, so there is no
 * circular Spring bean dependency (same reasoning {@code CustomerCreditService}'s own javadoc
 * gives for depending on repositories directly elsewhere).
 */
@Service
public class SalesOrderReturnService {

    private final SalesOrderReturnRepository salesOrderReturnRepository;
    private final SalesOrderReturnLineRepository salesOrderReturnLineRepository;
    private final SalesOrderService salesOrderService;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final AccountingService accountingService;
    /** Multi-warehouse phase 3 — where returned goods go back, and the last fulfilment's location as the default. */
    private final WarehouseService warehouseService;
    private final SalesOrderFulfillmentRepository salesOrderFulfillmentRepository;

    public SalesOrderReturnService(SalesOrderReturnRepository salesOrderReturnRepository,
                                    SalesOrderReturnLineRepository salesOrderReturnLineRepository,
                                    SalesOrderService salesOrderService,
                                    CatalogService catalogService,
                                    AuditLogService auditLogService,
                                    AccountingService accountingService,
                                    WarehouseService warehouseService,
                                    SalesOrderFulfillmentRepository salesOrderFulfillmentRepository) {
        this.salesOrderReturnRepository = salesOrderReturnRepository;
        this.salesOrderReturnLineRepository = salesOrderReturnLineRepository;
        this.salesOrderService = salesOrderService;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
        this.salesOrderFulfillmentRepository = salesOrderFulfillmentRepository;
    }

    @Transactional(readOnly = true)
    public List<SalesOrderReturnDto> listAll() {
        return salesOrderReturnRepository.findAllByOrderByReturnDateDesc().stream().map(SalesOrderReturnDto::from).toList();
    }

    /** Task 3's own return history — the Sale Order detail screen's return history section. */
    @Transactional(readOnly = true)
    public List<SalesOrderReturnDto> listForOrder(Long salesOrderId) {
        return salesOrderReturnRepository.findBySalesOrderIdOrderByReturnDateDesc(salesOrderId).stream()
                .map(SalesOrderReturnDto::from).toList();
    }

    /** A single return's own detail, for a return receipt/print view. */
    @Transactional(readOnly = true)
    public SalesOrderReturnDto getById(Long id) {
        return SalesOrderReturnDto.from(getEntityOrThrow(id));
    }

    /**
     * Every Sales Order line id on this order that has ever had anything returned, mapped to the
     * total quantity returned — backs the "already returned" figure on the order detail screen's
     * "process a return" panel, same shape as {@code ReturnsService.returnedQuantitiesForOrder}.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> returnedQuantitiesForOrder(Long salesOrderId) {
        return salesOrderReturnLineRepository.findBySalesOrderId(salesOrderId).stream()
                .collect(Collectors.groupingBy(l -> l.getOrderLine().getId(), Collectors.summingInt(SalesOrderReturnLine::getQuantity)));
    }

    /**
     * Only the *fulfilled* portion of a line can be returned — a Sales Order line that was never
     * fulfilled was never handed over, so there is nothing physical to bring back (matching how
     * {@code ReturnsService.processReturn} only ever returns against a completed POS sale's own
     * line quantity). Reverses stock for exactly the quantity being returned (+qty, the same
     * direction {@code PurchaseOrderService.receiveLines} moves stock, since it's coming back into
     * the store) and never touches the Sales Order's own status — a return is a side-ledger, same
     * as the POS return leaves the original sale's own status alone. The order itself must be
     * CONFIRMED/PARTIALLY_FULFILLED/FULFILLED — i.e. it must have actually committed the customer
     * to something before any of it can be returned.
     */
    @Transactional
    public SalesOrderReturnDto processReturn(ProcessSalesOrderReturnRequest request) {
        SalesOrder so = salesOrderService.getEntityOrThrow(request.getSalesOrderId());
        if (so.getStatus() != SalesOrderStatus.CONFIRMED
                && so.getStatus() != SalesOrderStatus.PARTIALLY_FULFILLED
                && so.getStatus() != SalesOrderStatus.FULFILLED) {
            throw ApiException.badRequest("Cannot process a return against sales order " + so.getOrderNumber() + " — it is " + so.getStatus());
        }

        SalesOrderReturn salesOrderReturn = new SalesOrderReturn();
        salesOrderReturn.setSalesOrder(so);
        salesOrderReturn.setReason(request.getReason());
        // Own "SO-RET-" + millis shape — see SalesOrderReturn.returnNumber's javadoc.
        salesOrderReturn.setReturnNumber("SO-RET-" + System.currentTimeMillis());

        // Multi-warehouse phase 3: where the goods go back. An explicit choice wins (any active
        // location — a DAMAGED one is a legitimate destination for goods that came back broken);
        // otherwise the location the order was last fulfilled from; null = legacy primary stock.
        // Deliberately not subject to the "no Main warehouse" rule fulfilment follows: a return must
        // never be stranded because the setting was switched on after the goods went out.
        WarehouseLocation returnLocation;
        if (request.getLocationId() != null) {
            returnLocation = warehouseService.getLocationOrThrow(request.getLocationId());
            if (!returnLocation.isActive() || returnLocation.getWarehouse() == null || !returnLocation.getWarehouse().isActive()) {
                throw ApiException.badRequest("That location is inactive and cannot receive returned stock");
            }
        } else {
            returnLocation = salesOrderFulfillmentRepository.findFirstBySalesOrderOrderByCreatedAtDesc(so)
                    .map(SalesOrderFulfillment::getLocation)
                    .orElse(null);
        }
        if (returnLocation != null) {
            salesOrderReturn.setLocation(returnLocation);
            salesOrderReturn.setLocationName(returnLocation.getName());
            salesOrderReturn.setWarehouseName(returnLocation.getWarehouse().getName());
        }

        BigDecimal refundTotal = BigDecimal.ZERO;
        // Accounting's Inventory-restocked/COGS-reversal figure for trackStock lines only — the
        // product's live costPrice, same "no cost snapshot on this line type" reasoning
        // SalesOrderService.fulfillLines already uses; see AccountingService.postSalesOrderReturn.
        BigDecimal cogsReversal = BigDecimal.ZERO;
        StringBuilder returnedDescription = new StringBuilder();
        for (ProcessSalesOrderReturnRequest.ReturnLineEntry entry : request.getLines()) {
            SalesOrderLine line = so.getLines().stream()
                    .filter(l -> l.getId().equals(entry.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Line " + entry.getLineId() + " does not belong to sales order " + so.getOrderNumber()));

            int alreadyReturned = salesOrderReturnLineRepository.sumReturnedQuantityForLine(line.getId());
            int remaining = line.getQuantityFulfilled() - alreadyReturned;
            if (entry.getQuantity() > remaining) {
                throw ApiException.badRequest("Cannot return " + entry.getQuantity() + " of '" + line.getProductName()
                        + "' — only " + remaining + " remaining (of " + line.getQuantityFulfilled() + " fulfilled, " + alreadyReturned + " already returned)");
            }

            BigDecimal lineRefund = line.getUnitPrice()
                    .multiply(BigDecimal.valueOf(entry.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            refundTotal = refundTotal.add(lineRefund);

            SalesOrderReturnLine returnLine = new SalesOrderReturnLine();
            returnLine.setSalesOrderReturn(salesOrderReturn);
            returnLine.setOrderLine(line);
            returnLine.setQuantity(entry.getQuantity());
            returnLine.setRefundAmount(lineRefund);
            salesOrderReturn.getLines().add(returnLine);

            // Fourier: "Add track stock attribute on Product" — a non-tracked product's fulfillment
            // never deducted stock, so returning it doesn't restock anything either; see
            // Product.trackStock's javadoc.
            if (line.getProduct() != null && line.getProduct().isTrackStock()) {
                // Task 6: "Stock Movement History" — snapshot before/after, see SalesOrderReturnLine's javadoc.
                int stockBefore = returnLocation != null
                        ? warehouseService.getStockQuantity(line.getProduct(), returnLocation)
                        : line.getProduct().getStockQuantity();
                catalogService.adjustStock(line.getProduct(), entry.getQuantity(), returnLocation);
                returnLine.setStockBefore(stockBefore);
                returnLine.setStockAfter(returnLocation != null
                        ? warehouseService.getStockQuantity(line.getProduct(), returnLocation)
                        : line.getProduct().getStockQuantity());
                if (line.getProduct().getCostPrice() != null) {
                    cogsReversal = cogsReversal.add(
                            line.getProduct().getCostPrice().multiply(BigDecimal.valueOf(entry.getQuantity())));
                }
            }
            if (returnedDescription.length() > 0) returnedDescription.append(", ");
            returnedDescription.append(entry.getQuantity()).append(" x ").append(line.getProductName());
        }
        salesOrderReturn.setRefundTotal(refundTotal);

        SalesOrderReturn saved = salesOrderReturnRepository.save(salesOrderReturn);
        auditLogService.record("SALES_ORDER_RETURN_PROCESSED",
                "Processed return " + saved.getReturnNumber() + " (" + returnedDescription + ") against sales order "
                        + so.getOrderNumber() + " for " + (so.getCustomer() != null ? so.getCustomer().getName() : "—")
                        + " (refund due back " + refundTotal + ")",
                "SalesOrderReturn", saved.getId());

        // Fourier: "all...Sale...module will be integrated when accounting is enabled" — see
        // AccountingService.postSalesOrderReturn's own javadoc.
        accountingService.postSalesOrderReturn(saved, refundTotal, cogsReversal.setScale(2, RoundingMode.HALF_UP));

        return SalesOrderReturnDto.from(saved);
    }

    private SalesOrderReturn getEntityOrThrow(Long id) {
        return salesOrderReturnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Sales order return not found: " + id));
    }
}
