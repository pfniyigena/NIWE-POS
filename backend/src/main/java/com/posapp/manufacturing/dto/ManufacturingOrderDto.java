package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.ManufacturingOrder;
import com.posapp.manufacturing.ManufacturingOrderConsumption;
import com.posapp.manufacturing.ManufacturingOrderOverhead;
import com.posapp.manufacturing.ManufacturingOrderOperation;
import com.posapp.manufacturing.ManufacturingOrderRun;
import com.posapp.manufacturing.ManufacturingOrderRunByProduct;
import com.posapp.manufacturing.ManufacturingOrderRunOperation;
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
        BigDecimal quantityPlanned,
        BigDecimal quantityProduced,
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
        String warehouseName,
        /** MF-2: people's and machines' time, summed across the runs, and what the order was expected to cost when it was confirmed. */
        BigDecimal totalLabourCost,
        BigDecimal totalMachineCost,
        BigDecimal plannedMaterialCost,
        BigDecimal plannedLabourCost,
        BigDecimal plannedMachineCost,
        BigDecimal plannedOverheadCost,
        BigDecimal plannedTotalCost,
        List<OperationDto> operations,
        /** MF-4: good units are quantityProduced; these are the rejects, what they cost, the share of units started that came out good, and what by-products took off the cost. */
        BigDecimal totalScrapped,
        BigDecimal totalScrapCost,
        BigDecimal yieldPercent,
        BigDecimal totalByProductCredit
) {
    public record RunByProductDto(Long productId, String productName, BigDecimal quantity, BigDecimal costValue, BigDecimal stockBefore, BigDecimal stockAfter) {
        public static RunByProductDto from(ManufacturingOrderRunByProduct b) {
            return new RunByProductDto(b.getProduct() != null ? b.getProduct().getId() : null, b.getProductName(), b.getQuantity(), b.getCostValue(), b.getStockBefore(), b.getStockAfter());
        }
    }

    public record OperationDto(int sequence, String name, Long workstationId, String workstationName,
                               BigDecimal minutesPerUnit, BigDecimal machineRate, BigDecimal labourRate) {
        public static OperationDto from(ManufacturingOrderOperation o) {
            return new OperationDto(o.getSequence(), o.getName(), o.getWorkstationId(), o.getWorkstationName(),
                    o.getMinutesPerUnit(), o.getMachineRate(), o.getLabourRate());
        }
    }

    public record RunOperationDto(int sequence, String name, String workstationName, BigDecimal plannedMinutes,
                                  BigDecimal actualMinutes, BigDecimal machineCost, BigDecimal labourCost) {
        public static RunOperationDto from(ManufacturingOrderRunOperation o) {
            return new RunOperationDto(o.getSequence(), o.getName(), o.getWorkstationName(), o.getPlannedMinutes(),
                    o.getActualMinutes(), o.getMachineCost(), o.getLabourCost());
        }
    }

    public record OverheadLineDto(Long overheadCostId, String name, OverheadAllocationMethod method, BigDecimal rate) {
        public static OverheadLineDto from(ManufacturingOrderOverhead o) {
            var oc = o.getOverheadCost();
            return new OverheadLineDto(oc != null ? oc.getId() : null, o.getNameSnapshot(), o.getMethodSnapshot(), o.getRateSnapshot());
        }
    }

    public record ConsumptionDto(Long componentId, String componentName, BigDecimal quantityConsumed, BigDecimal unitCost, BigDecimal stockBefore, BigDecimal stockAfter,
                                 BigDecimal plannedQuantity, BigDecimal varianceQuantity, BigDecimal varianceCost) {
        public static ConsumptionDto from(ManufacturingOrderConsumption c) {
            var comp = c.getComponent();
            BigDecimal planned = c.getPlannedQuantity() != null ? c.getPlannedQuantity() : c.getQuantityConsumed();
            BigDecimal variance = c.getQuantityConsumed().subtract(planned);
            BigDecimal unit = c.getUnitCost() != null ? c.getUnitCost() : BigDecimal.ZERO;
            return new ConsumptionDto(comp != null ? comp.getId() : null, c.getComponentName(), c.getQuantityConsumed(), c.getUnitCost(), c.getStockBefore(), c.getStockAfter(),
                    planned, variance, unit.multiply(variance).setScale(2, RoundingMode.HALF_UP));
        }
    }

    public record RunDto(Long id, Instant producedAt, BigDecimal quantityProduced, BigDecimal materialCost, BigDecimal overheadCost,
                          BigDecimal labourCost, BigDecimal machineCost,
                          BigDecimal totalCost, BigDecimal stockBefore, BigDecimal stockAfter, List<ConsumptionDto> consumptions,
                          List<RunOperationDto> operations,
                          BigDecimal quantityScrapped, String scrapReason, BigDecimal scrapCost, BigDecimal yieldPercent,
                          BigDecimal byProductCredit, List<RunByProductDto> byProducts) {
        public static RunDto from(ManufacturingOrderRun run) {
            return new RunDto(run.getId(), run.getCreatedAt(), run.getQuantityProduced(), run.getMaterialCost(), run.getOverheadCost(),
                    nz(run.getLabourCost()), nz(run.getMachineCost()),
                    run.getTotalCost(), run.getStockBefore(), run.getStockAfter(),
                    run.getConsumptions().stream().map(ConsumptionDto::from).toList(),
                    run.getOperations().stream().map(RunOperationDto::from).toList(),
                    run.getQuantityScrapped(), run.getScrapReason(), nz(run.getScrapCost()),
                    yieldOf(run.getQuantityProduced(), run.getQuantityScrapped()), nz(run.getByProductCredit()),
                    run.getByProducts().stream().map(RunByProductDto::from).toList());
        }
    }

    /** Good units as a percentage of units started; null when nothing was started. */
    static BigDecimal yieldOf(BigDecimal good, BigDecimal scrapped) {
        BigDecimal started = good.add(scrapped);
        return started.signum() > 0 ? good.multiply(BigDecimal.valueOf(100)).divide(started, 1, RoundingMode.HALF_UP) : null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public static ManufacturingOrderDto from(ManufacturingOrder mo) {
        var bom = mo.getBom();
        var routing = mo.getRouting();
        List<RunDto> runs = mo.getRuns().stream().map(RunDto::from).toList();
        BigDecimal totalMaterial = runs.stream().map(RunDto::materialCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOverhead = runs.stream().map(RunDto::overheadCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLabour = runs.stream().map(RunDto::labourCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMachine = runs.stream().map(RunDto::machineCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = totalMaterial.add(totalOverhead).add(totalLabour).add(totalMachine);
        BigDecimal plannedMaterial = nz(mo.getPlannedMaterialCost());
        BigDecimal plannedLabour = nz(mo.getPlannedLabourCost());
        BigDecimal plannedMachine = nz(mo.getPlannedMachineCost());
        BigDecimal plannedOverhead = nz(mo.getPlannedOverheadCost());
        BigDecimal totalScrapped = runs.stream().map(RunDto::quantityScrapped).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalScrapCost = runs.stream().map(RunDto::scrapCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = runs.stream().map(RunDto::byProductCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unitCost = mo.getQuantityProduced().signum() > 0
                ? totalCost.subtract(totalCredit).divide(mo.getQuantityProduced(), 2, RoundingMode.HALF_UP)
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
                mo.getLocation() != null ? mo.getLocation().getId() : null, mo.getLocationName(), mo.getWarehouseName(),
                totalLabour, totalMachine, plannedMaterial, plannedLabour, plannedMachine, plannedOverhead,
                plannedMaterial.add(plannedLabour).add(plannedMachine).add(plannedOverhead),
                mo.getOperations().stream().map(OperationDto::from).toList(),
                totalScrapped, totalScrapCost, yieldOf(mo.getQuantityProduced(), totalScrapped), totalCredit
        );
    }
}
