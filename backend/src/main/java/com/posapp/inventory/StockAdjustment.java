package com.posapp.inventory;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * Task: "Inventory depth ... stock adjustments/write-offs with a reason code." A direct correction
 * to a product's stock quantity that isn't a sale, a return, or a purchase-order receipt — a
 * recount, breakage, theft, expiry, or anything else the {@link StockAdjustmentReason#OTHER}
 * catch-all covers. {@code quantityDelta} can be negative (the common case — writing off loss) or
 * positive (a recount that found more stock than the system had).
 *
 * <p>Fourier: "Build a full permissions system" — {@link #status} gates everything below it, same
 * shape as {@code com.posapp.restaurant.RestaurantWastage}. {@code StockAdjustmentService.create}
 * only ever sets {@link StockAdjustmentStatus#PENDING} and never actually touches real stock or
 * Accounting — {@code stockBefore}/{@code stockAfter} at that point are a *preview* (what the
 * change would be if applied right now), not yet the real effect. Only {@code
 * StockAdjustmentService.confirm} — which moves the status to {@link StockAdjustmentStatus#CONFIRMED}
 * — recomputes and applies the real delta (re-checking current stock, since it may have moved
 * since this was logged) and posts to Accounting; {@code reject} moves it to {@link
 * StockAdjustmentStatus#REJECTED} instead, with no side effects at all. Who made/confirmed/rejected
 * it is left to AuditLogService, not duplicated here.
 */
@Getter
@Setter
@Entity
public class StockAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at adjustment time, so a later product rename doesn't rewrite this trail entry. */
    private String productName;

    private int quantityDelta;

    /**
     * Fourier: multi-warehouse phase 2 — the specific location this adjustment applies to.
     * Nullable on purpose: null means the legacy behaviour (the product's plain, primary-warehouse
     * stock figure), which is what every adjustment created before this column existed did, and
     * what a single-warehouse shop keeps doing. When set, {@link #stockBefore}/{@link #stockAfter}
     * are that location's own quantities.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "location_id")
    private com.posapp.warehouse.WarehouseLocation location;

    /** Snapshots of the location and its warehouse's names at logging time (null alongside a null {@link #location}), so renaming either later doesn't rewrite this trail entry. */
    private String locationName;

    private String warehouseName;

    /** A preview at PENDING time, the real pre-adjustment stock once CONFIRMED — see this class's own javadoc. */
    private int stockBefore;

    /** A preview at PENDING time, the real post-adjustment stock once CONFIRMED — see this class's own javadoc. */
    private int stockAfter;

    @Enumerated(EnumType.STRING)
    private StockAdjustmentReason reason;

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    private StockAdjustmentStatus status = StockAdjustmentStatus.PENDING;

    /** Set only by {@code StockAdjustmentService.confirm} — when the real stock/accounting impact actually happened, as opposed to {@code getCreatedAt()} which is only when it was logged. */
    private java.time.Instant confirmedAt;

    /** Increase of a BATCH product: the lot number (defaults to ADJ-<id>) and its optional expiry. */
    @jakarta.persistence.Column(name = "tracking_batch")
    private String trackingBatch;

    @jakarta.persistence.Column(name = "tracking_expiry")
    private java.time.LocalDate trackingExpiry;

    /** SERIAL products: the serial numbers added (increase) or taken out (decrease), one per line. */
    @jakarta.persistence.Column(name = "tracking_serials", columnDefinition = "TEXT")
    private String trackingSerials;
}
