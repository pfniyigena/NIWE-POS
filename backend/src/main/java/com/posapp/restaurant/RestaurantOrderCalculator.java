package com.posapp.restaurant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The money math of a restaurant bill, kept in one place so the screen, the receipt, the reports and the accounting posting always
 * agree. Every figure is worked out from the lines and the few pricing fields kept on the order; nothing is stored twice.
 *
 * <p>Order of the arithmetic: each line is price x quantity ({@link #lineGross}); the line's own discount comes off first; the order
 * discount (a percentage, or a fixed amount spread over the lines in proportion to what they cost) comes off next, which gives
 * {@link #lineTotal}. Tax is worked out line by line on that final figure, exactly as before. The service charge is a percentage of the
 * items' price before tax (tax on the service charge, if any, is a separate percentage). Rounding to the nearest multiple of the
 * configured unit is applied last, to the amount the guest pays.
 */
public final class RestaurantOrderCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RestaurantOrderCalculator() {
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Price x quantity, before any discount. */
    public static BigDecimal lineGross(RestaurantOrderLine line) {
        return line.getUnitPrice().add(nz(line.getOptionsPrice())).multiply(line.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** The line's own discount (a percentage of its gross). */
    public static BigDecimal lineOwnDiscount(RestaurantOrderLine line) {
        BigDecimal pct = nz(line.getDiscountPercent());
        if (pct.signum() <= 0) return BigDecimal.ZERO.setScale(SCALE);
        return lineGross(line).multiply(pct).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal afterOwn(RestaurantOrderLine line) {
        return lineGross(line).subtract(lineOwnDiscount(line));
    }

    /** The share of every line's price the order discount takes off (0 to 1). */
    public static BigDecimal orderDiscountRatio(RestaurantOrder order) {
        if (order == null) return BigDecimal.ZERO;
        BigDecimal pct = nz(order.getDiscountPercent());
        BigDecimal amount = nz(order.getDiscountAmount());
        if (pct.signum() <= 0 && amount.signum() <= 0) return BigDecimal.ZERO;
        if (pct.signum() > 0) return pct.min(HUNDRED).divide(HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal sum = BigDecimal.ZERO;
        for (RestaurantOrderLine l : order.getLines()) sum = sum.add(afterOwn(l));
        if (sum.signum() <= 0) return BigDecimal.ZERO;
        return amount.divide(sum, 8, RoundingMode.HALF_UP).min(BigDecimal.ONE);
    }

    /** What the guest is charged for the line's price: gross less the line discount and its share of the order discount. */
    public static BigDecimal lineTotal(RestaurantOrderLine line) {
        BigDecimal after = afterOwn(line);
        BigDecimal ratio = orderDiscountRatio(line.getOrder());
        if (ratio.signum() == 0) return after.setScale(SCALE, RoundingMode.HALF_UP);
        return after.multiply(BigDecimal.ONE.subtract(ratio)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Everything taken off this line, from its own discount and its share of the order discount. */
    public static BigDecimal lineDiscount(RestaurantOrderLine line) {
        return lineGross(line).subtract(lineTotal(line));
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
        return cost.multiply(line.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** The items' price before tax, after discounts. */
    public static BigDecimal subtotal(RestaurantOrder order) {
        BigDecimal total = BigDecimal.ZERO;
        for (RestaurantOrderLine line : order.getLines()) {
            total = total.add(lineNetTotal(line));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Tax on the items only. */
    public static BigDecimal itemTax(RestaurantOrder order) {
        BigDecimal total = BigDecimal.ZERO;
        for (RestaurantOrderLine line : order.getLines()) {
            total = total.add(lineTax(line));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Everything taken off the bill by discounts. */
    public static BigDecimal discountTotal(RestaurantOrder order) {
        BigDecimal total = BigDecimal.ZERO;
        for (RestaurantOrderLine line : order.getLines()) {
            total = total.add(lineDiscount(line));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal serviceCharge(RestaurantOrder order) {
        BigDecimal pct = nz(order.getServiceChargePercent());
        if (order.isServiceChargeWaived() || pct.signum() <= 0) return BigDecimal.ZERO.setScale(SCALE);
        return subtotal(order).multiply(pct).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal serviceChargeTax(RestaurantOrder order) {
        BigDecimal vat = nz(order.getServiceChargeVatPercent());
        if (vat.signum() <= 0) return BigDecimal.ZERO.setScale(SCALE);
        return serviceCharge(order).multiply(vat).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    /** All the tax on the bill: on the items and on the service charge. */
    public static BigDecimal taxTotal(RestaurantOrder order) {
        return itemTax(order).add(serviceChargeTax(order)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Items + service charge + tax, before rounding. */
    public static BigDecimal totalBeforeRounding(RestaurantOrder order) {
        return subtotal(order).add(serviceCharge(order)).add(taxTotal(order));
    }

    /** What rounding adds to (or takes from) the bill; 0 when no rounding is set. */
    public static BigDecimal roundingAdjustment(RestaurantOrder order) {
        BigDecimal unit = nz(order.getRoundingUnit());
        BigDecimal pre = totalBeforeRounding(order);
        if (unit.signum() <= 0 || pre.signum() <= 0) return BigDecimal.ZERO.setScale(SCALE);
        BigDecimal rounded = pre.divide(unit, 0, RoundingMode.HALF_UP).multiply(unit);
        return rounded.subtract(pre).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** The amount the guest owes for the meal (without any tip). */
    public static BigDecimal total(RestaurantOrder order) {
        return totalBeforeRounding(order).add(roundingAdjustment(order)).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
