package com.posapp.register;

import com.posapp.common.BaseEntity;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import com.posapp.user.AppUser;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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

    /** A switched-off till cannot open a new shift; its history stays. Existing registers stay on. */
    private boolean active = true;

    /** The float this till normally starts with: pre-filled when a shift is opened. Null means no suggestion. */
    private BigDecimal defaultFloat;

    /** The most a shift may be opened with at this till. Null means no limit. */
    private BigDecimal maxFloat;

    /**
     * When not empty, only these people (and managers) can work this till; empty means anyone
     * allowed to sell. See CashierSessionService.assertCashierAllowed.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "register_cashier", joinColumns = @JoinColumn(name = "register_id"), inverseJoinColumns = @JoinColumn(name = "app_user_id"))
    private Set<AppUser> allowedCashiers = new HashSet<>();

    /**
     * Fourier: "map POS Terminal (till) with specific Device ... the Terminal can not be opened to
     * more than one device and it is known". When {@link #deviceId} is set, only that browser
     * (and, when {@link #deviceIp} is set, only from that address) may open the terminal, ring
     * sales or open a shift here — see DeviceBindingService. Null means unbound: any device may
     * work the till, exactly as before this field existed.
     */
    @Column(length = 64)
    private String deviceId;
    @Column(length = 120)
    private String deviceName;
    /** Address the device had when it was bound; null means the address is not checked. */
    @Column(length = 64)
    private String deviceIp;
    private Instant deviceBoundAt;
    @Column(length = 120)
    private String deviceBoundBy;
}
