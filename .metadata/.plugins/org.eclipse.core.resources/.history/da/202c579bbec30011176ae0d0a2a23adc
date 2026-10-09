package com.posapp.inventory;

import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import com.posapp.inventory.dto.PurchaseRequisitionDto;
import com.posapp.inventory.dto.PurchaseRequisitionRequest;
import com.posapp.inventory.dto.ReorderRequisitionRequest;
import com.posapp.inventory.dto.ReorderSuggestionDto;
import com.posapp.salesorder.StockReservationService;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-product reorder rules. A product with a reorder level is listed once its sellable stock (every
 * warehouse, damaged locations excluded) plus what is still due on open purchase orders is at or
 * below that level; the suggested quantity brings that same position back up to the maximum level
 * (twice the reorder level when no maximum is set). The suggestions can be turned into a draft
 * purchase requisition, which then follows the normal approval and conversion flow.
 */
@Service
public class ReorderService {

    private final ProductRepository productRepository;
    private final WarehouseService warehouseService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseRequisitionService purchaseRequisitionService;
    private final StockReservationService stockReservationService;
    private final com.posapp.warehouse.StockTransferRepository stockTransferRepository;

    public ReorderService(ProductRepository productRepository,
                          WarehouseService warehouseService,
                          PurchaseOrderRepository purchaseOrderRepository,
                          PurchaseRequisitionService purchaseRequisitionService,
                          StockReservationService stockReservationService,
                          com.posapp.warehouse.StockTransferRepository stockTransferRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.stockReservationService = stockReservationService;
        this.productRepository = productRepository;
        this.warehouseService = warehouseService;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseRequisitionService = purchaseRequisitionService;
    }

    @Transactional(readOnly = true)
    public List<ReorderSuggestionDto> suggestions() {
        Map<Long, Integer> onHand = warehouseService.sellableQuantitiesByProduct(null);
        Map<Long, Integer> onOrder = outstandingOnOrder();
        Map<Long, Integer> reserved = stockReservationService.reservedByProduct();
        // Stock on the road between branches will arrive, so it counts like stock on order.
        for (Object[] row : stockTransferRepository.inTransitByProduct()) {
            onOrder.merge(((Number) row[0]).longValue(), ((Number) row[1]).intValue(), Integer::sum);
        }
        List<ReorderSuggestionDto> result = new ArrayList<>();
        for (Product p : productRepository.findByActiveTrueAndTrackStockTrueAndReorderLevelNotNullOrderByNameAsc()) {
            if (p.isService()) continue;
            int level = p.getReorderLevel();
            int max = p.getMaxStockLevel() != null ? Math.max(p.getMaxStockLevel(), level) : level * 2;
            // Stock promised to customers on confirmed sales orders is not really ours to count.
            int have = onHand.getOrDefault(p.getId(), 0) - reserved.getOrDefault(p.getId(), 0);
            int due = onOrder.getOrDefault(p.getId(), 0);
            int position = have + due;
            if (position > level) continue;
            int suggested = Math.max(max - position, 1);
            result.add(new ReorderSuggestionDto(p.getId(), p.getSku(), p.getName(),
                    p.getCategory() != null ? p.getCategory().getName() : null, have, due, level, max, suggested));
        }
        result.sort(Comparator.comparingInt((ReorderSuggestionDto r) -> r.onHand() + r.onOrder() - r.reorderLevel()));
        return result;
    }

    /** Creates a DRAFT requisition from the chosen products and quantities. */
    @Transactional
    public PurchaseRequisitionDto createRequisition(ReorderRequisitionRequest request) {
        PurchaseRequisitionRequest pr = new PurchaseRequisitionRequest();
        pr.setNotes((request.getNotes() == null || request.getNotes().isBlank()) ? "Created from the Reorder report" : request.getNotes());
        List<PurchaseRequisitionRequest.LineEntry> lines = new ArrayList<>();
        for (ReorderRequisitionRequest.Entry e : request.getLines()) {
            PurchaseRequisitionRequest.LineEntry line = new PurchaseRequisitionRequest.LineEntry();
            line.setProductId(e.getProductId());
            line.setQuantityRequested(e.getQuantity());
            line.setNote("Reorder");
            lines.add(line);
        }
        pr.setLines(lines);
        return purchaseRequisitionService.createDraft(pr);
    }

    private Map<Long, Integer> outstandingOnOrder() {
        Map<Long, Integer> result = new HashMap<>();
        for (PurchaseOrder po : purchaseOrderRepository.findByStatusIn(List.of(PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED))) {
            for (PurchaseOrderLine line : po.getLines()) {
                if (line.getProduct() == null) continue;
                int outstanding = line.getQuantityOrdered() - line.getQuantityReceived();
                if (outstanding > 0) result.merge(line.getProduct().getId(), outstanding, Integer::sum);
            }
        }
        return result;
    }
}
