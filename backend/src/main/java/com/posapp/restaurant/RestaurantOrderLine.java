package com.posapp.restaurant;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One ordered item on a {@link RestaurantOrder} — snapshots the product's name/price/tax at
 * add-time (same "never let a later product edit rewrite an already-rung line" convention as
 * {@code com.posapp.order.OrderLine}; see that class's javadoc). {@code kitchenStatus} is this
 * line's own Kitchen Order Ticket lifecycle (Fourier: "Kitchen Order Tickets / Kitchen Display")
 * — tracked per line, not per whole order, since a table can send a second round of drinks while
 * their mains are still being plated.
 */
@Getter
@Setter
@Entity
public class RestaurantOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RestaurantOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private String description;
    private BigDecimal unitPrice;
    private BigDecimal unitCost;

    private String taxCode;
    private BigDecimal taxRatePercent;
    private Boolean taxInclusive;

    private BigDecimal quantity = BigDecimal.ONE;

    /** Special instructions for the kitchen ("no onions", "extra spicy"...) — free text, never validated. */
    private String notes;

    /** A discount on this line, in percent, with the reason and who gave it. */
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private String discountReason;
    private String discountBy;

    /** What the guest added on top of the plain item, copied at order time. */
    @jakarta.persistence.OrderBy("id ASC")
    @jakarta.persistence.OneToMany(mappedBy = "line", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    private java.util.List<RestaurantOrderLineOption> options = new java.util.ArrayList<>();

    /** Sum of the picked options' prices for ONE unit; added to the unit price in every figure. */
    private BigDecimal optionsPrice = BigDecimal.ZERO;

    /** Which course this line belongs to (1 = first). Lines of a later course wait until the course is fired. */
    private int course = 1;

    /** Which seat at the table ordered this (optional), so a bill can be split by seat. */
    private Integer seat;

    /** Given free by a manager (a 100% discount with a reason). */
    private boolean comped = false;

    /** The menu item this line was ordered from (used to check its options later). */
    private Long menuItemId;

    /** Allergen tags copied from the menu item at order time. */
    private String allergens;

    @Enumerated(EnumType.STRING)
    private KitchenStatus kitchenStatus = KitchenStatus.PENDING;

    /** Snapshot of MenuItem.station at add-line time (Fourier: "Kitchen stations") — see that field's javadoc for why this is snapshotted rather than looked up live. */
    private String station;

    /**
     * Fourier: "Restaurant should impact Inventory Module" — this line's own product's stock
     * immediately before/after checkout applied this line's {@code -quantity} to it (see {@code
     * RestaurantOrderService#checkout}), same "snapshot at event time" convention as {@code
     * com.posapp.order.OrderLine.stockBefore}/{@code stockAfter}, so {@code
     * ReportService.stockMovementHistory} can show a "Restaurant sale" row that still reads
     * correctly after later events move the product's stock again. Both null when this line's
     * product has an active recipe instead (see {@code RestaurantOrderConsumption} — the
     * ingredients are what gets a snapshot then, not this line's own product), when the product
     * isn't stock-tracked, had no product at all, or was rung before this feature existed.
     */
    private BigDecimal stockBefore;
    private BigDecimal stockAfter;

    /** Snapshot of MenuItem.prepMinutes (null = the restaurant default at the time it is read). */
    private Integer prepMinutes;
    /** When the ticket reached the kitchen, started, was ready and was served. */
    private java.time.Instant sentAt;
    private java.time.Instant preparingAt;
    private java.time.Instant readyAt;
    private java.time.Instant servedAt;
    /** How many times the dish was sent back to be made again. */
    private int remakeCount = 0;

    /** Moves the ticket to a new status and stamps the time, so the kitchen's waiting times can be worked out. */
    public void moveTo(KitchenStatus status) {
        java.time.Instant now = java.time.Instant.now();
        this.kitchenStatus = status;
        switch (status) {
            case PENDING -> { sentAt = null; preparingAt = null; readyAt = null; servedAt = null; }
            case SENT -> { if (sentAt == null) sentAt = now; preparingAt = null; readyAt = null; servedAt = null; }
            case PREPARING -> { if (sentAt == null) sentAt = now; if (preparingAt == null) preparingAt = now; readyAt = null; servedAt = null; }
            case READY -> { if (sentAt == null) sentAt = now; readyAt = now; servedAt = null; }
            case SERVED -> { if (sentAt == null) sentAt = now; if (readyAt == null) readyAt = now; servedAt = now; }
        }
    }
}
