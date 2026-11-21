package com.posapp.salesorder;
import com.posapp.common.Qty;

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

import com.posapp.common.TaxMath;
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
    @org.springframework.beans.factory.annotation.Autowired
    private com.posapp.tracking.TrackingService trackingService;

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
    public Map<Long, BigDecimal> returnedQuantitiesForOrder(Long salesOrderId) {
        return salesOrderReturnLineRepository.findBySalesOrderId(salesOrderId).stream()
                .collect(Collectors.groupingBy(l -> l.getOrderLine().getId(), Collectors.reducing(BigDecimal.ZERO, SalesOrderReturnLine::getQuantity, BigDecimal::add)));
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
        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        // Accounting's Inventory-restocked/COGS-reversal figure for trackStock lines only: the cost the goods had when they were sold
        // (v1.74.0, SalesOrderLine.costTotal), else the product's cost price today; see AccountingService.postSalesOrderReturn.
        BigDecimal cogsReversal = BigDecimal.ZERO;
        StringBuilder returnedDescription = new StringBuilder();
        for (ProcessSalesOrderReturnRequest.ReturnLineEntry entry : request.getLines()) {
            SalesOrderLine line = so.getLines().stream()
                    .filter(l -> l.getId().equals(entry.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Line " + entry.getLineId() + " does not belong to sales order " + so.getOrderNumber()));

            BigDecimal alreadyReturned = salesOrderReturnLineRepository.sumReturnedQuantityForLine(line.getId());
            BigDecimal remaining = line.getQuantityFulfilled().subtract(alreadyReturned);
            if (entry.getQuantity().compareTo(remaining) > 0) {
                throw ApiException.badRequest("Cannot return " + Qty.str(entry.getQuantity()) + " of '" + line.getProductName()
                        + "' — only " + Qty.str(remaining) + " remaining (of " + Qty.str(line.getQuantityFulfilled()) + " fulfilled, " + Qty.str(alreadyReturned) + " already returned)");
            }

            // v1.70.0: the same figures the fulfilment posted for these units: line / order discount first, then the tax (on top or inside the price).
            // The refund is the customer's money back, tax included; the books reverse the net sale and the VAT separately (see postSalesOrderReturn).
            boolean inclusive = Boolean.TRUE.equals(line.getTaxInclusive());
            BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(so.getDiscountPercent(), line.getDiscountPercent());
            BigDecimal grossReturned = TaxMath.applyDiscount(line.getUnitPrice().multiply(entry.getQuantity()), discountPercent);
            BigDecimal taxReturned = TaxMath.taxOnAmount(grossReturned, line.getTaxRatePercent(), inclusive).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netReturned = TaxMath.netOfTax(grossReturned, taxReturned, inclusive).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineRefund = netReturned.add(taxReturned);
            refundTotal = refundTotal.add(lineRefund);
            netTotal = netTotal.add(netReturned);
            taxTotal = taxTotal.add(taxReturned);

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
                BigDecimal stockBefore = returnLocation != null
                        ? warehouseService.getStockQuantity(line.getProduct(), returnLocation)
                        : line.getProduct().getStockQuantity();
                catalogService.adjustStock(line.getProduct(), entry.getQuantity(), returnLocation);
                if (entry.getSerialNumbers() != null && !entry.getSerialNumbers().isEmpty()
                        && com.posapp.tracking.TrackingService.modeOf(line.getProduct()) == com.posapp.tracking.TrackingMode.SERIAL) {
                    // v1.67.0: the units that came back are in stock again under their own serial numbers
                    if (BigDecimal.valueOf(trackingService.cleanSerials(line.getProduct(), entry.getSerialNumbers()).size()).compareTo(entry.getQuantity()) != 0) {
                        throw ApiException.badRequest("'" + line.getProductName() + "': " + Qty.str(entry.getQuantity()) + " returned but " + entry.getSerialNumbers().size() + " serial number(s) given");
                    }
                    WarehouseLocation back = returnLocation != null ? returnLocation
                            : warehouseService.resolveCanonicalSalesLocation(warehouseService.getDefaultWarehouseOrThrow());
                    trackingService.restoreSerials(line.getProduct(), back, entry.getSerialNumbers(), so.getOrderNumber());
                }
                returnLine.setStockBefore(stockBefore);
                returnLine.setStockAfter(returnLocation != null
                        ? warehouseService.getStockQuantity(line.getProduct(), returnLocation)
                        : line.getProduct().getStockQuantity());
                // v1.74.0: at the cost the goods had when they were sold (average of the fulfilments of this line); lines fulfilled before this version have no
                // record of it and keep using the product's cost price today
                if (line.getCostQuantity() != null && line.getCostQuantity().signum() > 0) {
                    cogsReversal = cogsReversal.add(line.getCostTotal().multiply(entry.getQuantity()).divide(line.getCostQuantity(), 4, RoundingMode.HALF_UP));
                } else if (line.getProduct().getCostPrice() != null) {
                    cogsReversal = cogsReversal.add(
                            line.getProduct().getCostPrice().multiply(entry.getQuantity()));
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
        accountingService.postSalesOrderReturn(saved, netTotal, taxTotal, cogsReversal.setScale(2, RoundingMode.HALF_UP));

        return SalesOrderReturnDto.from(saved);
    }

    private SalesOrderReturn getEntityOrThrow(Long id) {
        return salesOrderReturnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Sales order return not found: " + id));
    }
}
