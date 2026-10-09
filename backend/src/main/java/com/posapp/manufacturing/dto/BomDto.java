package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.Bom;
import com.posapp.manufacturing.BomLine;

import java.math.BigDecimal;
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
        List<LineDto> lines
) {
    public record LineDto(Long id, Long componentId, String componentName, String componentSku, int quantityPerUnit, BigDecimal componentCostPrice) {
        public static LineDto from(BomLine line) {
            var c = line.getComponent();
            return new LineDto(line.getId(), c.getId(), c.getName(), c.getSku(), line.getQuantityPerUnit(), c.getCostPrice());
        }
    }

    public static BomDto from(Bom bom) {
        List<LineDto> lines = bom.getLines().stream().map(LineDto::from).toList();
        BigDecimal estimatedCost = lines.stream()
                .map(l -> l.componentCostPrice().multiply(BigDecimal.valueOf(l.quantityPerUnit())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var routing = bom.getRouting();
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
                lines
        );
    }
}
