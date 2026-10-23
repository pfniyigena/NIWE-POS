package com.posapp.manufacturing.dto;


import java.math.BigDecimal;/** How much of one component would be short if a plan line's {@code suggestedQuantity} were produced right now, against today's stock — see ProductionPlanDto's class javadoc. */
public record ComponentShortfallDto(
        Long componentId,
        String componentName,
        boolean trackStock,
        BigDecimal requiredQuantity,
        BigDecimal availableQuantity,
        /** max(0, requiredQuantity - availableQuantity) — zero, not negative, when there's already enough on hand. Always zero for a component with trackStock off, since it's never actually checked at production time either. */
        BigDecimal shortfall
) {
}
