package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.Bom;
import com.posapp.manufacturing.BomByProduct;
import com.posapp.manufacturing.BomLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record BomDto(
        Long id,
        Long productId,
        String productName,
        String code,
        String name,
        Long routingId,
        String routingName,
        boolean active,
        /** Sum of component.costPrice * quantityPerUnit across every line — an estimate of this Bom's material cost to make one unit, at TODAY's component cost prices (not a snapshot; see ManufacturingOrderRun for the snapshotted figure an actual production run locks in). */
        BigDecimal estimatedUnitMaterialCost,
        List<LineDto> lines,
        /** MF-4 */
        List<ByProductDto> byProducts,
        /** MF-6 */
        int version,
        /** Time on the routing at today's workstation and labour rates, per finished unit (indirect costs are added per order, not here). */
        BigDecimal estimatedUnitLabourCost,
        BigDecimal estimatedUnitMachineCost,
        /** Material + labour + machine, per unit, before by-products. */
        BigDecimal estimatedUnitCost,
        /** The same less the share by-products take. */
        BigDecimal estimatedNetUnitCost,
        BigDecimal productPrice,
        /** (price - net cost) / price x 100; null when the product has no selling price. */
        BigDecimal estimatedMarginPercent
) {
    public record ByProductDto(Long id, Long productId, String productName, String productSku, BigDecimal quantityPerUnit, BigDecimal costSharePercent) {
        public static ByProductDto from(BomByProduct b) {
            var p = b.getProduct();
            return new ByProductDto(b.getId(), p.getId(), p.getName(), p.getSku(), b.getQuantityPerUnit(),
                    b.getCostSharePercent() != null ? b.getCostSharePercent() : BigDecimal.ZERO);
        }
    }

    public record LineDto(Long id, Long componentId, String componentName, String componentSku, BigDecimal quantityPerUnit, BigDecimal componentCostPrice, BigDecimal lineCost) {
        public static LineDto from(BomLine line) {
            var c = line.getComponent();
            BigDecimal cost = c.getCostPrice() != null ? c.getCostPrice() : BigDecimal.ZERO;
            return new LineDto(line.getId(), c.getId(), c.getName(), c.getSku(), line.getQuantityPerUnit(), c.getCostPrice(), cost.multiply(line.getQuantityPerUnit()));
        }
    }

    public static BomDto from(Bom bom) {
        List<LineDto> lines = bom.getLines().stream().map(LineDto::from).toList();
        BigDecimal estimatedCost = lines.stream()
                .map(l -> l.componentCostPrice().multiply(l.quantityPerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var routing = bom.getRouting();
        BigDecimal labour = BigDecimal.ZERO;
        BigDecimal machine = BigDecimal.ZERO;
        if (routing != null) {
            for (var op : routing.getOperations()) {
                BigDecimal minutes = op.getEstimatedMinutesPerUnit() != null ? op.getEstimatedMinutesPerUnit() : BigDecimal.ZERO;
                BigDecimal labourRate = op.getLabourHourlyRate() != null ? op.getLabourHourlyRate() : BigDecimal.ZERO;
                BigDecimal machineRate = op.getWorkstation() != null && op.getWorkstation().getHourlyRate() != null ? op.getWorkstation().getHourlyRate() : BigDecimal.ZERO;
                labour = labour.add(minutes.multiply(labourRate).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP));
                machine = machine.add(minutes.multiply(machineRate).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP));
            }
        }
        labour = labour.setScale(2, RoundingMode.HALF_UP);
        machine = machine.setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitCost = estimatedCost.add(labour).add(machine).setScale(2, RoundingMode.HALF_UP);
        BigDecimal share = bom.getByProducts().stream().map(b -> b.getCostSharePercent() != null ? b.getCostSharePercent() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = unitCost.subtract(unitCost.multiply(share).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        BigDecimal price = bom.getProduct().getUnitPrice() != null ? bom.getProduct().getUnitPrice() : BigDecimal.ZERO;
        BigDecimal margin = price.signum() > 0 ? price.subtract(net).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP) : null;
        return new BomDto(
                bom.getId(),
                bom.getProduct().getId(),
                bom.getProduct().getName(),
                bom.getCode(),
                bom.getName(),
                routing != null ? routing.getId() : null,
                routing != null ? routing.getName() : null,
                bom.isActive(),
                estimatedCost,
                lines,
                bom.getByProducts().stream().map(ByProductDto::from).toList(),
                bom.getVersion(), labour, machine, unitCost, net, price, margin
        );
    }
}
