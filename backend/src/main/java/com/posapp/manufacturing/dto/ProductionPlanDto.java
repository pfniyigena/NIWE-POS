package com.posapp.manufacturing.dto;

import java.time.Instant;
import java.util.List;

/**
 * Built entirely by {@code ProductionPlanService} (no static {@code from()} here) — unlike most
 * DTOs in this app, a plan line's component shortfalls need a live stock lookup plus {@code
 * BomService.requiredComponentQuantities} at *read* time (not generation time — see {@code
 * ProductionPlan}'s own javadoc for why that figure is deliberately always current), which a plain
 * entity-to-record mapper has no way to reach.
 */
public record ProductionPlanDto(
        Long id,
        String planNumber,
        Instant generatedAt,
        int lowStockThreshold,
        String note,
        List<LineDto> lines
) {
    public record LineDto(
            Long id,
            Long productId,
            String productName,
            Long bomId,
            String bomCode,
            int demandFromSales,
            int demandFromLowStock,
            int suggestedQuantity,
            Long generatedManufacturingOrderId,
            String generatedManufacturingOrderNumber,
            /** Live, as of when this plan was read — see class javadoc. Empty if this line has no Bom. */
            List<ComponentShortfallDto> componentShortfalls
    ) {
    }
}
