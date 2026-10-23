package com.posapp.manufacturing.dto;


import java.math.BigDecimal;import java.util.List;

/** MF-3: can the rest of this order be made from the stock on hand? One line per component, for the quantity still to produce. */
public record ComponentAvailabilityDto(
        Long orderId,
        String orderNumber,
        BigDecimal quantityRemaining,
        boolean canProduceAll,
        /** How many of the remaining units the current stock allows (never more than the remaining quantity). */
        BigDecimal maxProducible,
        boolean allowNegativeStock,
        List<Line> lines
) {
    public record Line(Long componentId, String componentName, boolean trackStock, BigDecimal perUnit, BigDecimal required, BigDecimal available, BigDecimal shortBy) {}
}
