package com.posapp.inventory;

import com.posapp.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Task: "Inventory depth ... A complete POS usually adds purchase orders and supplier records, a
 * stock-receiving flow that increments quantity with a paper trail." One order placed with one
 * {@link Supplier} for a set of products; {@link #status} tracks it from DRAFT through to fully
 * RECEIVED (or CANCELLED) — see {@link PurchaseOrderStatus}'s own javadoc for the lifecycle.
 * {@code orderNumber} is this PO's own human-readable identifier, assigned once at creation, same
 * "POS-"-style convention as {@code SaleOrder.invoiceNumber}/{@code SalesReturn.returnNumber} —
 * stable even if the supplier is later renamed or removed.
 */
@Getter
@Setter
@Entity
public class PurchaseOrder extends BaseEntity {

    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    private Instant orderDate = Instant.now();

    /** Nullable — an optional "when we expect this to arrive" note, purely informational (nothing here auto-flags an overdue PO). */
    private Instant expectedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — a single discount percentage applied
     * uniformly to every line on this order, populated only when {@code
     * StoreSettings.discountMode} was GLOBAL at the moment this order was created/edited (see
     * {@code PurchaseOrderLine.discountPercent}'s own javadoc for the PER_LINE case instead).
     * Zero (never null) for an order with no discount at all, or one entered per line instead.
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
     * otherwise. {@code PurchaseOrderService} converts this once, at save time, into the
     * equivalent {@link #discountPercent} (see {@code TaxMath.percentFromFixedAmount}) — this
     * field is kept only so re-opening the draft shows back the amount that was typed, not a
     * converted percentage.
     */
    private java.math.BigDecimal discountFixedAmount;

    /**
     * Fourier: multi-warehouse phase 2 — which warehouse this order is received into (its
     * RECEIVING location). Nullable on purpose: a null means "the primary warehouse", which is
     * both what every order created before this column existed did and what a single-warehouse
     * shop never has to think about. A hub-and-spoke business leaves it at the primary warehouse
     * (receive centrally, then Stock Transfer out to branches); a business receiving straight
     * into a branch picks that branch here. Chosen per order, editable only while DRAFT.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_warehouse_id")
    private com.posapp.warehouse.Warehouse receivingWarehouse;

    /**
     * Fourier: "How business branches can be introduced?" — the branch of the receiving warehouse, set whenever the order is saved.
     * Snapshot id (not a live reference); null reads as head office, which is also what every row
     * created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;

    /** Running total of landed costs (freight, duty, clearing) entered while receiving this order; spread over the delivered products' cost. */
    @jakarta.persistence.Column(name = "landed_cost_total")
    private java.math.BigDecimal landedCostTotal = java.math.BigDecimal.ZERO;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderLine> lines = new ArrayList<>();
}
