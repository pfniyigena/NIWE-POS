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
 * Fourier: "Restaurants need a dedicated wastage module." One recorded loss — a burnt batch, a
 * dropped plate, a spoiled ingredient — of either a menu item's own product or a recipe
 * ingredient; either way it's a plain {@link Product}, resolved and decremented the same way
 * {@code StockAdjustmentService} does for a generic write-off (via {@code
 * CatalogService.adjustStock}, respecting {@code StoreSettings.allowNegativeStock}), just with its
 * own restaurant-specific reason vocabulary (see {@link RestaurantWastageReason}) and its own
 * screen, kept separate from the general Inventory > Stock Adjustments screen the same way every
 * other Restaurant-module concept here (orders, receipts) has its own dedicated screen rather than
 * reusing the POS-side one. {@code quantity} is always the positive amount wasted — unlike {@code
 * StockAdjustment.quantityDelta}, wastage is never a gain.
 *
 * <p>Fourier: "Restaurant wastage should be confirmed by admin once confirmed inventory and
 * account will be impacted" — {@link #status} gates everything below it. {@code
 * RestaurantWastageService.create} only ever sets {@link RestaurantWastageStatus#PENDING} and
 * touches neither stock nor Accounting; {@code stockBefore}/{@code stockAfter}/{@code costImpact}/
 * {@code confirmedAt} stay null until an admin actually reviews it. Only {@code
 * RestaurantWastageService.confirm} — which moves the status to {@link
 * RestaurantWastageStatus#CONFIRMED} — decrements real stock (via {@code
 * CatalogService.adjustStock}, respecting {@code StoreSettings.allowNegativeStock}) and posts to
 * Accounting; {@code reject} moves it to {@link RestaurantWastageStatus#REJECTED} instead, with no
 * side effects at all. {@code stockBefore}/{@code stockAfter} are still null even once confirmed
 * when the product isn't stock-tracked (nothing to snapshot), same "no snapshot" case every other
 * trail entity in this app already has. {@code costImpact} is the snapshotted cost of the loss
 * ({@code Product.costPrice × quantity} at confirmation time) — zero when the product has no cost
 * price set — and is what {@code AccountingService.postRestaurantWastage} posts as the Inventory
 * Shrinkage Expense. Who recorded/confirmed/rejected it is left to {@code AuditLogService}, not
 * duplicated here, same convention as {@code StockAdjustment}'s own javadoc explains.
 */
@Getter
@Setter
@Entity
public class RestaurantWastage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at the moment this was recorded, so a later rename doesn't rewrite this trail entry. */
    private String productName;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private RestaurantWastageReason reason;

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    private RestaurantWastageStatus status = RestaurantWastageStatus.PENDING;

    /** Set only by {@code RestaurantWastageService.confirm} — when the real stock/accounting impact actually happened, as opposed to {@code getCreatedAt()} which is only when it was logged. */
    private java.time.Instant confirmedAt;

    /** Null until {@link RestaurantWastageStatus#CONFIRMED} (and stays null even then if the product isn't stock-tracked) — see this class's own javadoc. */
    private Integer stockBefore;

    private Integer stockAfter;

    /** Null until {@link RestaurantWastageStatus#CONFIRMED} — see this class's own javadoc. */
    private BigDecimal costImpact;

    /** Warehouse filter on Stock Movement History — the warehouse the confirmed wastage took stock from, snapshotted at confirmation; null = the Main warehouse (and every older row). */
    @jakarta.persistence.Column(name = "stock_warehouse_id")
    private Long stockWarehouseId;

    /**
     * Fourier: "How business branches can be introduced?" — the branch of the kitchen warehouse the loss was logged against.
     * Null = head office, which is what a row created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
