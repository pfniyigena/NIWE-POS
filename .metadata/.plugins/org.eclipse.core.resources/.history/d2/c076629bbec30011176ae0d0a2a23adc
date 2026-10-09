package com.posapp.salesorder;

import com.posapp.common.BaseEntity;
import com.posapp.customer.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * "Add Sale order... So that we can have POS & Sales" — a customer order taken by an admin/sales
 * rep outside the register, entirely separate from the POS {@code com.posapp.order.SaleOrder}
 * (which is deliberately untouched by this feature): no register/cashier session is involved at
 * any point, and stock only moves when a line is actually fulfilled (see {@link
 * SalesOrderService#fulfillLines}), not when the order is placed or confirmed — the same
 * order-vs-fulfillment split {@code com.posapp.inventory.PurchaseOrder} already draws on the
 * buying side. {@code orderNumber} uses its own {@code "SO-"} prefix (distinct from the POS sale's
 * {@code "POS-"}-style invoice numbers and Purchase Order's {@code "PO-"}) so the three kinds of
 * order never collide even though none of their tables are related.
 *
 * <p>Deliberately does not model tax at all, mirroring {@code PurchaseOrderLine}'s equally simple
 * treatment — kept distinct from the POS {@code SaleOrder}/{@code OrderCalculator}'s
 * inclusive-tax machinery, which has no equivalent need here. A Sales Order's whole committed
 * amount (every line's {@code quantityOrdered * unitPrice}, once CONFIRMED/PARTIALLY_FULFILLED/
 * FULFILLED — see {@link SalesOrderService#totalAmount}) is what the customer owes until paid
 * down via the existing {@code CustomerPayment}/{@code CustomerPaymentAllocation} mechanism —
 * there's no separate cash-at-sale-time vs. credit-portion split the way POS checkout has.
 */
@Getter
@Setter
@Entity
public class SalesOrder extends BaseEntity {

    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    private Instant orderDate = Instant.now();

    /** Nullable — an optional "when we expect to deliver/fulfill this" note, purely informational, same as PurchaseOrder.expectedDate. */
    private Instant expectedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — a single discount percentage applied
     * uniformly to every line on this order, populated only when {@code
     * StoreSettings.discountMode} was GLOBAL at the moment this order was created/edited (see
     * {@code SalesOrderLine.discountPercent}'s own javadoc for the PER_LINE case instead). Zero
     * (never null) for an order with no discount at all, or one entered per line instead — see
     * {@code StoreSettingsService.validateGlobalDiscount}, which enforces that only one of the
     * two is ever populated for a given document.
     */
    private java.math.BigDecimal discountPercent = java.math.BigDecimal.ZERO;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
     * default" — how {@link #discountPercent} above was actually entered while in GLOBAL mode:
     * typed directly as a percentage ({@code PERCENT}, the default), or converted from a flat
     * amount ({@code FIXED_AMOUNT} — see {@link #discountFixedAmount}). Purely a display/edit
     * round-trip concern; every line/tax calculation downstream still reads only the resolved
     * {@link #discountPercent}. Meaningless (and left at its default) outside GLOBAL mode.
     */
    @Enumerated(EnumType.STRING)
    private com.posapp.common.GlobalDiscountType discountType = com.posapp.common.GlobalDiscountType.PERCENT;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage" — the flat currency
     * amount the admin actually typed when {@link #discountType} is {@code FIXED_AMOUNT}; null
     * otherwise. {@code SalesOrderService} converts this once, at save time, into the equivalent
     * {@link #discountPercent} (see {@code TaxMath.percentFromFixedAmount}) — this field is kept
     * only so re-opening the draft shows back the amount that was typed, not a converted
     * percentage.
     */
    private java.math.BigDecimal discountFixedAmount;

    /**
     * Set once, at the moment this order is CONFIRMED (see {@link SalesOrderService#confirm}) — a
     * due date the customer's payment is expected by. Reuses the same {@code Customer
     * .defaultCreditTermDays} convention the POS credit sale already uses at checkout (completedAt
     * + that many days), so a Sales Order's amount owed shows up in the Outstanding/Overdue Credit
     * reports with a due date computed the same way a register credit sale's is.
     */
    private java.time.LocalDate dueDate;

    /**
     * Fourier: "How business branches can be introduced?" — the branch this order is sold from; chosen on the order, head office by default.
     * Snapshot id (not a live reference); null reads as head office, which is also what every row
     * created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderLine> lines = new ArrayList<>();
}
