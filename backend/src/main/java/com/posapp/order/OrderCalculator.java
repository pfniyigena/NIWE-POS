package com.posapp.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stateless money math — kept out of the entities so SaleOrder/OrderLine stay plain JPA data
 * holders. Tax is computed per line, from each line's own snapshotted {@code taxRatePercent}
 * (see OrderLine's javadoc) rather than one flat rate passed in externally — a product can be
 * assigned a different tax rate than another, or none at all.
 *
 * <p>A rate can be exclusive (the default: tax is added on top of {@code lineTotal} to get what
 * the customer pays) or inclusive (Task: "if it is true the in the tax in price is inclusive" —
 * {@code lineTotal} already has that tax folded into it, so the tax has to be extracted back out
 * rather than added again). See {@code OrderLine.taxInclusive}'s javadoc. Either way, {@code
 * lineNetTotal(line) + lineTax(line)} equals what the customer actually pays for that line, and
 * {@code subtotal(order) + taxTotal(order)} — unchanged as a formula — equals {@code total(order)}.
 */
public final class OrderCalculator {

    private static final int SCALE = 2;

    private OrderCalculator() {
    }

    public static BigDecimal lineTotal(OrderLine line) {
        BigDecimal gross = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
        BigDecimal discount = line.getDiscountPercent() == null ? BigDecimal.ZERO : line.getDiscountPercent();
        BigDecimal factor = BigDecimal.ONE.subtract(discount.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return gross.multiply(factor).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The tax on {@code amount} at {@code ratePercent}, either added on top (exclusive) or
     * extracted back out of an amount that already includes it (inclusive: {@code amount *
     * rate/(1+rate)}, the standard tax-inclusive-price formula). Null-safe — a tax-exempt (or
     * pre-per-product-tax) rate returns zero. Factored out of {@code lineTax} so ReturnsService can
     * apply the same rule to a partial (returned) slice of a line, not just a whole one.
     */
    public static BigDecimal taxOnAmount(BigDecimal amount, BigDecimal ratePercent, boolean inclusive) {
        if (ratePercent == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal fraction = ratePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        if (inclusive) {
            BigDecimal divisor = BigDecimal.ONE.add(fraction);
            return amount.multiply(fraction).divide(divisor, SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(fraction).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** This one line's own tax, from its snapshotted rate and inclusive/exclusive flag — zero if the line has none (tax-exempt product, or a pre-existing line from before per-product tax). */
    public static BigDecimal lineTax(OrderLine line) {
        return taxOnAmount(lineTotal(line), line.getTaxRatePercent(), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    /**
     * This line's pre-tax (net) contribution to the sale. For an exclusive-tax (or untaxed) line
     * this is just {@code lineTotal} — the price never had tax in it. For an inclusive-tax line,
     * {@code lineTotal} already includes the tax, so this backs it back out ({@code lineTotal -
     * lineTax}) — what the *displayed* price is worth once the embedded tax is set aside.
     */
    public static BigDecimal lineNetTotal(OrderLine line) {
        if (!Boolean.TRUE.equals(line.getTaxInclusive())) {
            return lineTotal(line);
        }
        return lineTotal(line).subtract(lineTax(line)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** This line's cost of goods (its snapshotted unitCost × quantity — never discounted, unlike revenue). Zero if unset (see OrderLine.unitCost). */
    public static BigDecimal lineCost(OrderLine line) {
        BigDecimal unitCost = line.getUnitCost();
        if (unitCost == null) {
            return BigDecimal.ZERO;
        }
        return unitCost.multiply(BigDecimal.valueOf(line.getQuantity())).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Revenue (post-discount, pre-tax — {@code lineNetTotal}, not {@code lineTotal}: for an
     * inclusive-tax line {@code lineTotal} still has the tax folded in, and tax collected is never
     * counted as profit) minus cost of goods for this one line — used by the Profit reports.
     */
    public static BigDecimal lineProfit(OrderLine line) {
        return lineNetTotal(line).subtract(lineCost(line)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal subtotal(SaleOrder order) {
        return order.getLines().stream()
                .map(OrderCalculator::lineNetTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal taxTotal(SaleOrder order) {
        return order.getLines().stream()
                .map(OrderCalculator::lineTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal total(SaleOrder order) {
        return subtotal(order).add(taxTotal(order)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static int totalQuantity(SaleOrder order) {
        return order.getLines().stream().mapToInt(OrderLine::getQuantity).sum();
    }
}
