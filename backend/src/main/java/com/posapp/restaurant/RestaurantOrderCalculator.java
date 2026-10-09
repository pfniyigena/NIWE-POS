package com.posapp.restaurant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Same money math as {@code com.posapp.order.OrderCalculator}, kept as its own small copy here
 * rather than shared, since {@link RestaurantOrderLine} is a distinct entity type OrderCalculator
 * isn't written against.
 */
public final class RestaurantOrderCalculator {

    private static final int SCALE = 2;

    private RestaurantOrderCalculator() {
    }

    public static BigDecimal lineTotal(RestaurantOrderLine line) {
        return line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTax(RestaurantOrderLine line) {
        if (line.getTaxRatePercent() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = lineTotal(line);
        BigDecimal fraction = line.getTaxRatePercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        boolean inclusive = Boolean.TRUE.equals(line.getTaxInclusive());
        if (inclusive) {
            BigDecimal divisor = BigDecimal.ONE.add(fraction);
            return total.multiply(fraction).divide(divisor, SCALE, RoundingMode.HALF_UP);
        }
        return total.multiply(fraction).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineNetTotal(RestaurantOrderLine line) {
        boolean inclusive = Boolean.TRUE.equals(line.getTaxInclusive());
        return inclusive ? lineTotal(line).subtract(lineTax(line)) : lineTotal(line);
    }

    public static BigDecimal lineCost(RestaurantOrderLine line) {
        BigDecimal cost = line.getUnitCost() != null ? line.getUnitCost() : BigDecimal.ZERO;
        return cost.multiply(BigDecimal.valueOf(line.getQuantity())).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal subtotal(RestaurantOrder order) {
        BigDecimal total = BigDecimal.ZERO;
        for (RestaurantOrderLine line : order.getLines()) {
            total = total.add(lineNetTotal(line));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal taxTotal(RestaurantOrder order) {
        BigDecimal total = BigDecimal.ZERO;
        for (RestaurantOrderLine line : order.getLines()) {
            total = total.add(lineTax(line));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal total(RestaurantOrder order) {
        return subtotal(order).add(taxTotal(order));
    }
}
