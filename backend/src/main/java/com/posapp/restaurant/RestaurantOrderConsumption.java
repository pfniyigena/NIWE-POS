package com.posapp.restaurant;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One ingredient actually consumed by one {@link RestaurantOrderLine} at checkout, when that
 * line's product has an active recipe (Fourier: "Therefore, the restaurant can automatically
 * calculate ingredient consumption... Also Restaurant should impact Inventory Module") — the
 * restaurant counterpart to {@code com.posapp.manufacturing.ManufacturingOrderConsumption}, same
 * reasoning: this is what lets {@code ReportService.stockMovementHistory} show one dated
 * "Restaurant ingredient consumption" row per ingredient per checkout, rather than the ingredient
 * stock simply changing with no structured trail. {@code componentName}/{@code unitCost} are
 * snapshotted at consumption time, same "don't rewrite history when the product later changes"
 * convention as every other trail entity in this app. {@code stockBefore}/{@code stockAfter} are
 * null when the ingredient isn't stock-tracked ({@link Product#isTrackStock()} false) — see
 * {@code RestaurantOrderService#checkout} for why nothing is deducted for one, mirroring how a
 * non-tracked component already produces no snapshot in Manufacturing.
 */
@Getter
@Setter
@Entity
public class RestaurantOrderConsumption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RestaurantOrder order;

    /** Which line's recipe this ingredient was consumed for — purely informational (which dish drove this consumption), never required to compute anything. */
    @ManyToOne(fetch = FetchType.LAZY)
    private RestaurantOrderLine line;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product component;

    private String componentName;

    private int quantityConsumed;

    /** Snapshot of component.costPrice at the moment of this checkout — see class javadoc. */
    private BigDecimal unitCost = BigDecimal.ZERO;

    private Integer stockBefore;

    private Integer stockAfter;
}
