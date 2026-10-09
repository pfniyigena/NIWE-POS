package com.posapp.register;

import com.posapp.common.BaseEntity;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * Fourier: "I want to make sure that the terminal will be registered on warehouse. But the stock
 * will be managed on Location ... if the item is sold, it will be reduced on the specific
 * location ... Everytime there is purchase, the default location of the main warehouse will be
 * impacted." {@link #warehouse} is nullable and never required — a register with none assigned
 * keeps behaving exactly as it did before this field existed: every sale and return at it still
 * targets the primary warehouse's default location (see OrderService/ReturnsService's own
 * "null/unassigned means the primary warehouse" fallback), so no existing register needs any
 * admin action to keep working. When set, a sale at this terminal deducts stock from this
 * warehouse's default location unless {@link #location} narrows it further (see that field's own
 * javadoc), and a return against a sale rung here restocks the same spot. Purchases, Stock
 * Adjustments, Manufacturing, Packaging and Restaurant consumption are deliberately unaffected by
 * either field — they all keep going through CatalogService.adjustStock's plain (no-location)
 * overload, which always targets the primary warehouse's default location, regardless of which
 * register, if any, is involved.
 */
@Getter
@Setter
@Entity
public class Register extends BaseEntity {
    private String name;
    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    private Warehouse warehouse;

    /**
     * Fourier: "if multi-location then sale start from location" — optionally narrows a sale at
     * this terminal to one specific location of {@link #warehouse} (the admin sees only that
     * warehouse's own locations to pick from in Admin > Registers — see RegisterService's own
     * resolve/validation). Nullable and never required, same "no existing register needs any
     * admin action to keep working" reasoning as {@link #warehouse} itself: left unset, a sale at
     * this terminal keeps targeting its warehouse's *default* location exactly as it always has.
     * Must belong to {@link #warehouse} when both are set — enforced in RegisterService, not here.
     * Named {@code defaultLocation} on the wire (RegisterDto/RegisterRequest) to avoid colliding
     * with the pre-existing free-text {@link #location} string field above, which is an
     * unstructured "where is this till physically sitting" label (e.g. "Front counter") entirely
     * unrelated to {@code com.posapp.warehouse.WarehouseLocation}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseLocation defaultLocation;
}
