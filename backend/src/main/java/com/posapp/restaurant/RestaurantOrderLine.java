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

    private int quantity = 1;

    /** Special instructions for the kitchen ("no onions", "extra spicy"...) — free text, never validated. */
    private String notes;

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
    private Integer stockBefore;
    private Integer stockAfter;
}
