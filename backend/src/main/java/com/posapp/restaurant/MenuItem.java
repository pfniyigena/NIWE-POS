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
 * One existing catalog {@link Product} placed on the restaurant's menu, under a {@link
 * MenuCategory} (Fourier chose "reuse existing products" over a separate menu-item concept when
 * asked) — this is purely an organizing/display layer, never a copy of the product's own data.
 * {@code priceOverride} is the one thing that can differ here: a restaurant sometimes charges a
 * different price for the same product dine-in vs. off-the-shelf (a plated meal vs. its packaged
 * retail price, say) — null means "use the product's own unitPrice," the same nullable-override
 * convention every other optional-override field in this app already uses.
 */
@Getter
@Setter
@Entity
public class MenuItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private MenuCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private int sortOrder = 0;

    private boolean active = true;

    private BigDecimal priceOverride;

    /**
     * Free-text kitchen prep area (Fourier: "Kitchen stations") — "Grill", "Salad", "Bar", "Fryer"
     * etc, same free-text convention as {@code DiningTable.section}. Null means unassigned; the
     * Kitchen Display's station filter then falls back to grouping those under "Unassigned".
     * Snapshotted onto {@code RestaurantOrderLine.station} at add-line time, same convention as
     * every other line field, so a later edit here never rewrites an already-fired ticket.
     */
    private String station;
}
