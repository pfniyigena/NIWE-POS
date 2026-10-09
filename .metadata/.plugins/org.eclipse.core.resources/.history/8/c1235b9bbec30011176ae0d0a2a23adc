package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.ManufacturingOrder;
import com.posapp.manufacturing.ManufacturingOrderConsumption;
import com.posapp.manufacturing.ManufacturingOrderOverhead;
import com.posapp.manufacturing.ManufacturingOrderRun;
import com.posapp.manufacturing.ManufacturingOrderStatus;
import com.posapp.manufacturing.OverheadAllocationMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ManufacturingOrderDto(
        Long id,
        String orderNumber,
        Long productId,
        String productName,
        Long bomId,
        String bomCode,
        Long routingId,
        String routingName,
        int quantityPlanned,
        int quantityProduced,
        ManufacturingOrderStatus status,
        LocalDate scheduledDate,
        Instant completedAt,
        String note,
        Long sourcePlanLineId,
        List<OverheadLineDto> overheads,
        List<RunDto> runs,
        /** Sums across every run — see ManufacturingOrderRun's javadoc for why each run's own figures are never recomputed later. */
        BigDecimal totalMaterialCost,
        BigDecimal totalOverheadCost,
        BigDecimal totalCost,
        /** totalCost / quantityProduced, or null while quantityProduced is still zero (nothing to divide by yet). */
        BigDecimal unitCost,
        /** Multi-warehouse phase 4 — null for an order working on the primary warehouse's normal stock (all older orders). */
        Long locationId,
        String locationName,
        String warehouseName
) {
    public record OverheadLineDto(Long overheadCostId, String name, OverheadAllocationMethod method, BigDecimal rate) {
        public static OverheadLineDto from(ManufacturingOrderOverhead o) {
            var oc = o.getOverheadCost();
            return new OverheadLineDto(oc != null ? oc.getId() : null, o.getNameSnapshot(), o.getMethodSnapshot(), o.getRateSnapshot());
        }
    }

    public record ConsumptionDto(Long componentId, String componentName, int quantityConsumed, BigDecimal unitCost, Integer stockBefore, Integer stockAfter) {
        public static ConsumptionDto from(ManufacturingOrderConsumption c) {
            var comp = c.getComponent();
            return new ConsumptionDto(comp != null ? comp.getId() : null, c.getComponentName(), c.getQuantityConsumed(), c.getUnitCost(), c.getStockBefore(), c.getStockAfter());
        }
    }

    public record RunDto(Long id, Instant producedAt, int quantityProduced, BigDecimal materialCost, BigDecimal overheadCost,
                          BigDecimal totalCost, Integer stockBefore, Integer stockAfter, List<ConsumptionDto> consumptions) {
        public static RunDto from(ManufacturingOrderRun run) {
            return new RunDto(run.getId(), run.getCreatedAt(), run.getQuantityProduced(), run.getMaterialCost(), run.getOverheadCost(),
                    run.getTotalCost(), run.getStockBefore(), run.getStockAfter(),
                    run.getConsumptions().stream().map(ConsumptionDto::from).toList());
        }
    }

    public static ManufacturingOrderDto from(ManufacturingOrder mo) {
        var bom = mo.getBom();
        var routing = mo.getRouting();
        List<RunDto> runs = mo.getRuns().stream().map(RunDto::from).toList();
        BigDecimal totalMaterial = runs.stream().map(RunDto::materialCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOverhead = runs.stream().map(RunDto::overheadCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = totalMaterial.add(totalOverhead);
        BigDecimal unitCost = mo.getQuantityProduced() > 0
                ? totalCost.divide(BigDecimal.valueOf(mo.getQuantityProduced()), 2, RoundingMode.HALF_UP)
                : null;
        return new ManufacturingOrderDto(
                mo.getId(), mo.getOrderNumber(),
                mo.getProduct().getId(), mo.getProduct().getName(),
                bom != null ? bom.getId() : null, bom != null ? bom.getCode() : null,
                routing != null ? routing.getId() : null, routing != null ? routing.getName() : null,
                mo.getQuantityPlanned(), mo.getQuantityProduced(), mo.getStatus(),
                mo.getScheduledDate(), mo.getCompletedAt(), mo.getNote(), mo.getSourcePlanLineId(),
                mo.getOverheads().stream().map(OverheadLineDto::from).toList(),
                runs, totalMaterial, totalOverhead, totalCost, unitCost,
                mo.getLocation() != null ? mo.getLocation().getId() : null, mo.getLocationName(), mo.getWarehouseName()
        );
    }
}
