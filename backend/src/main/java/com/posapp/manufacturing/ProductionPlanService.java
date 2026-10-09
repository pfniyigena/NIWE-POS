package com.posapp.manufacturing;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.dto.ComponentShortfallDto;
import com.posapp.manufacturing.dto.CreateManufacturingOrderRequest;
import com.posapp.manufacturing.dto.GenerateOrderFromPlanLineRequest;
import com.posapp.manufacturing.dto.GenerateProductionPlanRequest;
import com.posapp.manufacturing.dto.ManufacturingOrderDto;
import com.posapp.manufacturing.dto.ProductionPlanDto;
import com.posapp.salesorder.SalesOrder;
import com.posapp.salesorder.SalesOrderLine;
import com.posapp.salesorder.SalesOrderRepository;
import com.posapp.salesorder.SalesOrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manufacturing Module — "Production plans" (full MRP-style, per Fourier's confirmed answer:
 * aggregate demand across confirmed Sale Orders and low-stock finished goods, surface component
 * shortfalls; deliberately no auto-generated Purchase Orders and no multi-level BOM explosion —
 * see {@link ProductionPlan}'s class javadoc for the exact scoping). {@link #generate} is the MRP
 * run itself; {@link #generateManufacturingOrder} converts one reviewed line into a real {@link
 * ManufacturingOrder} when the admin is ready to actually commit to producing it.
 */
@Service
public class ProductionPlanService {

    private static final List<SalesOrderStatus> OPEN_DEMAND_STATUSES = List.of(SalesOrderStatus.CONFIRMED, SalesOrderStatus.PARTIALLY_FULFILLED);

    private final ProductionPlanRepository repository;
    private final BomService bomService;
    private final SalesOrderRepository salesOrderRepository;
    private final ManufacturingOrderService manufacturingOrderService;
    private final AuditLogService auditLogService;

    public ProductionPlanService(ProductionPlanRepository repository, BomService bomService,
                                  SalesOrderRepository salesOrderRepository,
                                  ManufacturingOrderService manufacturingOrderService,
                                  AuditLogService auditLogService) {
        this.repository = repository;
        this.bomService = bomService;
        this.salesOrderRepository = salesOrderRepository;
        this.manufacturingOrderService = manufacturingOrderService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ProductionPlanDto> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProductionPlanDto getById(Long id) {
        return toDto(getEntityOrThrow(id));
    }

    /**
     * The MRP run: for every product with an active Bom, {@code demandFromSales} is its total
     * unfulfilled quantity across every CONFIRMED/PARTIALLY_FULFILLED {@code SalesOrderLine}, and
     * {@code demandFromLowStock} tops up whatever's left of current stock (after first covering
     * sales demand) to the requested threshold. A product whose Bom is inactive, that has no Bom
     * at all, that isn't stock-tracked (see {@code Product.trackStock} — never stock-constrained,
     * so never worth planning for), or whose combined demand comes out to zero is simply left off
     * the plan.
     */
    @Transactional
    public ProductionPlanDto generate(GenerateProductionPlanRequest request) {
        int threshold = request.getLowStockThreshold();

        Map<Long, Integer> salesDemandByProductId = new LinkedHashMap<>();
        for (SalesOrder so : salesOrderRepository.findByStatusIn(OPEN_DEMAND_STATUSES)) {
            for (SalesOrderLine line : so.getLines()) {
                int outstanding = line.getQuantityOrdered() - line.getQuantityFulfilled();
                if (outstanding > 0 && line.getProduct() != null) {
                    salesDemandByProductId.merge(line.getProduct().getId(), outstanding, Integer::sum);
                }
            }
        }

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanNumber("PLAN-" + System.currentTimeMillis());
        plan.setLowStockThreshold(threshold);
        plan.setNote(request.getNote());

        for (Bom bom : bomService.listActiveBoms()) {
            Product product = bom.getProduct();
            if (!product.isTrackStock()) {
                continue;
            }
            int demandFromSales = salesDemandByProductId.getOrDefault(product.getId(), 0);
            int availableStock = product.getStockQuantity();
            int salesShortfall = Math.max(0, demandFromSales - availableStock);
            int remainingAfterSales = Math.max(0, availableStock - demandFromSales);
            int lowStockShortfall = Math.max(0, threshold - remainingAfterSales);
            int suggested = salesShortfall + lowStockShortfall;
            if (suggested <= 0) {
                continue;
            }
            ProductionPlanLine line = new ProductionPlanLine();
            line.setPlan(plan);
            line.setProduct(product);
            line.setProductName(product.getName());
            line.setBom(bom);
            line.setDemandFromSales(demandFromSales);
            line.setDemandFromLowStock(lowStockShortfall);
            line.setSuggestedQuantity(suggested);
            plan.getLines().add(line);
        }

        ProductionPlan saved = repository.save(plan);
        auditLogService.record("PRODUCTION_PLAN_GENERATED",
                "Generated production plan " + saved.getPlanNumber() + " (" + saved.getLines().size() + " line" + (saved.getLines().size() == 1 ? "" : "s") + ")",
                "ProductionPlan", saved.getId());
        return toDto(saved);
    }

    /** Converts one reviewed plan line into a real DRAFT Manufacturing Order — see class javadoc. */
    @Transactional
    public ManufacturingOrderDto generateManufacturingOrder(Long planId, Long lineId, GenerateOrderFromPlanLineRequest request) {
        ProductionPlan plan = getEntityOrThrow(planId);
        ProductionPlanLine line = plan.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Line " + lineId + " does not belong to plan " + plan.getPlanNumber()));
        if (line.getGeneratedManufacturingOrder() != null) {
            throw ApiException.badRequest("A manufacturing order was already generated from this line: " + line.getGeneratedManufacturingOrder().getOrderNumber());
        }
        if (line.getBom() == null) {
            throw ApiException.badRequest("'" + line.getProductName() + "' has no bill of materials yet — add one before generating an order");
        }
        CreateManufacturingOrderRequest createRequest = new CreateManufacturingOrderRequest();
        createRequest.setBomId(line.getBom().getId());
        createRequest.setQuantityPlanned(request.getQuantity());
        createRequest.setScheduledDate(request.getScheduledDate());
        createRequest.setNote("Generated from production plan " + plan.getPlanNumber());
        createRequest.setSourcePlanLineId(line.getId());
        createRequest.setOverheadCostIds(request.getOverheadCostIds());
        ManufacturingOrderDto created = manufacturingOrderService.create(createRequest);
        line.setGeneratedManufacturingOrder(manufacturingOrderService.getEntityOrThrow(created.id()));
        repository.save(plan);
        auditLogService.record("PRODUCTION_PLAN_ORDER_GENERATED",
                "Generated manufacturing order " + created.orderNumber() + " from production plan " + plan.getPlanNumber(),
                "ProductionPlan", plan.getId());
        return created;
    }

    private ProductionPlan getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Production plan not found: " + id));
    }

    /** Component shortfalls are computed live against today's stock — see ProductionPlanDto's class javadoc for why. */
    private ProductionPlanDto toDto(ProductionPlan plan) {
        List<ProductionPlanDto.LineDto> lines = plan.getLines().stream().map(line -> {
            List<ComponentShortfallDto> shortfalls = List.of();
            if (line.getBom() != null) {
                Map<Product, Integer> required = bomService.requiredComponentQuantities(line.getBom(), line.getSuggestedQuantity());
                shortfalls = required.entrySet().stream().map(entry -> {
                    Product component = entry.getKey();
                    int requiredQty = entry.getValue();
                    int available = component.getStockQuantity();
                    int shortfall = component.isTrackStock() ? Math.max(0, requiredQty - available) : 0;
                    return new ComponentShortfallDto(component.getId(), component.getName(), component.isTrackStock(), requiredQty, available, shortfall);
                }).toList();
            }
            var mo = line.getGeneratedManufacturingOrder();
            return new ProductionPlanDto.LineDto(
                    line.getId(), line.getProduct().getId(), line.getProductName(),
                    line.getBom() != null ? line.getBom().getId() : null,
                    line.getBom() != null ? line.getBom().getCode() : null,
                    line.getDemandFromSales(), line.getDemandFromLowStock(), line.getSuggestedQuantity(),
                    mo != null ? mo.getId() : null, mo != null ? mo.getOrderNumber() : null,
                    shortfalls
            );
        }).toList();
        return new ProductionPlanDto(plan.getId(), plan.getPlanNumber(), plan.getCreatedAt(), plan.getLowStockThreshold(), plan.getNote(), lines);
    }
}
