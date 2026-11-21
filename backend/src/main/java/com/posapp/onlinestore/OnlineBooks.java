package com.posapp.onlinestore;

import com.posapp.accounting.AccountingService;
import com.posapp.i18n.I18n;
import com.posapp.salesorder.SalesOrderRepository;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderLineDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * v1.73.0: the parts of an online order that the sale order's own entries do not show as accounts of their own: the discounts given (coupons and loyalty points),
 * the delivery income, and the loyalty points the shop owes. Everything here is a reclassification or a liability entry: it never changes the total income of an
 * order, only shows where it came from.
 */
@Service
public class OnlineBooks {

    @Autowired
    private AccountingService accounting;
    @Autowired
    private SalesOrderRepository salesOrders;

    @Autowired
    private OnlineStoreSettingsRepository settingsRepository;
    @Autowired
    private com.posapp.branch.BranchService branchService;

    /** v1.74.0: the branch the online shop sells for (Shop settings), or null (head office) when none is chosen or the chosen one is no longer active. */
    public Long shopBranch() {
        Long id = settingsRepository.findAll().stream().findFirst().map(OnlineStoreSettings::getBranchId).orElse(null);
        return id != null && branchService.listActive().stream().anyMatch(b -> b.id().equals(id)) ? id : null;
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** The branch an order's money belongs to: that of its sale order once it has one, otherwise the shop's branch. */
    Long branchFor(OnlineOrder o) {
        return o != null && o.getSalesOrderId() != null ? branchOf(o) : shopBranch();
    }

    /** The branch of the order's sale order (null = head office). */
    Long branchOf(OnlineOrder o) {
        if (o == null || o.getSalesOrderId() == null) return null;
        return salesOrders.findById(o.getSalesOrderId()).map(com.posapp.salesorder.SalesOrder::getBranchId).orElse(null);
    }

    /** What a sale-order amount is before tax: the tax rate of the product's line, when the line's prices include tax. */
    private static BigDecimal beforeTax(BigDecimal amount, Long productId, SalesOrderDto so) {
        if (so != null) {
            for (SalesOrderLineDto sl : so.lines()) {
                if (productId.equals(sl.productId()) && sl.taxInclusive() && sl.taxRatePercent() != null && sl.taxRatePercent().signum() > 0) {
                    return amount.multiply(HUNDRED).divide(HUNDRED.add(sl.taxRatePercent()), 2, RoundingMode.HALF_UP);
                }
            }
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** The coupon and points discount that belongs to {@code qty} units of this line, in the sale order's terms and before tax. */
    BigDecimal discountNet(OnlineOrderLine l, BigDecimal qty, SalesOrderDto so) {
        BigDecimal disc = l.getDiscountAmount() == null ? BigDecimal.ZERO : l.getDiscountAmount();
        BigDecimal gross = l.getLineTotal();
        if (disc.signum() <= 0 || gross == null || gross.signum() <= 0 || l.getQuantity().signum() <= 0 || qty.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal inSaleOrderTerms = l.getBasePrice().multiply(l.getQuantity()).multiply(disc).divide(gross, 4, RoundingMode.HALF_UP)
                .multiply(qty).divide(l.getQuantity(), 2, RoundingMode.HALF_UP);
        return beforeTax(inSaleOrderTerms, l.getProductId(), so);
    }

    /** The order was delivered: show the discount given and the delivery fee apart from sales. {@code so} is the sale order as it stands after the delivery. */
    @Transactional
    public void orderDelivered(OnlineOrder o, SalesOrderDto so, Long deliveryProductId) {
        BigDecimal discount = BigDecimal.ZERO;
        for (OnlineOrderLine l : o.getLines()) {
            BigDecimal delivered = l.getDeliveredQuantity() == null ? l.getQuantity() : l.getDeliveredQuantity();
            discount = discount.add(discountNet(l, delivered, so));
        }
        BigDecimal fee = o.getDeliveryFee() == null ? BigDecimal.ZERO : o.getDeliveryFee();
        BigDecimal delivery = fee.signum() > 0 && deliveryProductId != null ? beforeTax(fee, deliveryProductId, so) : BigDecimal.ZERO;
        accounting.postOnlineSaleSplit(o.getId(), discount, delivery, I18n.t("Discounts and delivery income of online order {0}", o.getCode()), so == null ? branchOf(o) : so.branchId());
    }

    /** Goods came back: the discount that belonged to them is no longer given. */
    @Transactional
    public void returnProcessed(OnlineOrder o, OnlineReturn r, SalesOrderDto so) {
        BigDecimal discount = BigDecimal.ZERO;
        for (OnlineReturnLine rl : r.getLines()) {
            OnlineOrderLine ol = o.getLines().stream().filter(x -> x.getId().equals(rl.getOnlineOrderLineId())).findFirst().orElse(null);
            if (ol != null) discount = discount.add(discountNet(ol, rl.getQuantity(), so));
        }
        accounting.postOnlineDiscountReversal(r.getId(), discount, I18n.t("Discount of returned goods, return {0}", r.getCode()) + " (online order " + o.getCode() + ")", so == null ? branchOf(o) : so.branchId());
    }

    /**
     * Loyalty points moved on a customer's account. {@code earned}: points that the customer now holds (earned, or given back); otherwise points that are gone (spent
     * on a delivered order, or taken back after a return).
     */
    @Transactional
    public void loyalty(String sourceType, Long sourceId, OnlineOrder o, long points, boolean earned, String note) {
        accounting.postLoyaltyPoints(sourceType, sourceId, points, earned, note + (o == null ? "" : " (online order " + o.getCode() + ")"), branchOf(o));
    }
}
