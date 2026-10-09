package com.posapp.order;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class OrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SaleOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product name/price at the time it was added, so a later price/name edit doesn't rewrite history. */
    private String description;
    private BigDecimal unitPrice;

    /**
     * Snapshot of the product's assigned tax rate at the moment this line was added — same
     * reasoning as description/unitPrice above, and the same code-not-foreign-key pattern
     * {@code Payment.method} uses: a later change to the product's tax assignment, a rename of
     * the rate, or even deactivating it, never rewrites an already-rung line. Both null if the
     * product had no tax rate assigned (tax-exempt) at add-line time.
     */
    private String taxCode;
    private BigDecimal taxRatePercent;

    /**
     * Snapshot of the assigned tax rate's {@code inclusive} flag at add-line time — same
     * snapshot-not-live-reference reasoning as taxCode/taxRatePercent above. {@code true} means
     * {@code unitPrice} already had this rate's tax folded in when the line was added, so
     * {@code OrderCalculator} extracts the tax instead of adding it on top. Null (not just false)
     * for a tax-exempt line or one added before this field existed — {@code OrderCalculator}
     * treats null the same as false (exclusive/no tax), so nothing about existing lines changes.
     */
    private Boolean taxInclusive;

    /**
     * Snapshot of the product's cost price at the moment this line was added — same
     * snapshot-not-live-reference reasoning as taxCode/taxRatePercent above, so a later edit to a
     * product's cost never rewrites a past sale's reported profit (see the Profit reports).
     * Null for lines added before this field existed; treated as zero cost by OrderCalculator.
     */
    private BigDecimal unitCost;

    /**
     * Snapshot of the product's EFRIS commodity/tariff code at add-line time — see
     * Product.unspCode's javadoc. Same reasoning as every other per-line snapshot: a later edit
     * to the product's code never rewrites what was actually sent for a past sale.
     */
    private String unspCode;

    /**
     * Snapshot of the product's goods/service flag at add-line time — see Product.service's
     * javadoc (EFRIS Chad's {@code item_nature}). Same reasoning as every other per-line snapshot.
     */
    private boolean service = false;

    private int quantity = 1;
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Task 6: "Any transaction affecting stock should be shown to Stock Movement History (Pos
     * sales...)" — this product's stock immediately before/after checkout applied this line's
     * {@code -quantity} to it (see {@code OrderService#completeCheckout}), snapshotted the same
     * way {@code StockReceipt.stockBefore}/{@code stockAfter} are, so {@code
     * ReportService.stockMovementHistory} can show a POS sale row that still reads correctly after
     * later events move the product's stock again. Both null for a line with no product, or one
     * added/checked out before this feature existed.
     */
    private Integer stockBefore;
    private Integer stockAfter;

    /** Serial numbers chosen at the till for a serial-numbered product, one per line of text. Empty = the oldest in stock are used. */
    @jakarta.persistence.Column(name = "serial_numbers", columnDefinition = "TEXT")
    private String serialNumbers;
}
