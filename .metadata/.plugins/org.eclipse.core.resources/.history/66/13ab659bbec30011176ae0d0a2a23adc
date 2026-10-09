package com.posapp.warehouse;

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
 * Fourier: "multi-location/multi-warehouse tracking ... Stock Transfers", then later: "User can
 * make stock transfer to any location" — moves a quantity of one product from one {@link
 * WarehouseLocation} to another, same or different warehouse. Originally warehouse-to-warehouse
 * only (always each warehouse's own default location); generalized to location-to-location so a
 * transfer between two locations under the *same* warehouse is possible too, which the original
 * shape couldn't express at all. Same PENDING/CONFIRMED/REJECTED review shape as {@code
 * com.posapp.inventory.StockAdjustment} (see that class's own javadoc for the general pattern):
 * {@code StockTransferService.create} only ever logs this as {@link StockTransferStatus#PENDING}
 * — neither location's stock is touched yet. Only {@code StockTransferService.confirm} — which
 * moves the status to {@link StockTransferStatus#CONFIRMED} — actually applies the movement
 * (decrementing {@link #fromLocation}, incrementing {@link #toLocation}), re-checking the source
 * location's current stock first since it may have moved since this was logged; {@code reject}
 * moves it to {@link StockTransferStatus#REJECTED} with no stock effect at all.
 *
 * <p>Deliberately posts nothing to Accounting, unlike a confirmed {@code StockAdjustment} — a
 * transfer relocates stock without changing the store's total inventory value (there's no
 * separate inventory asset account per warehouse/location in this app's chart of accounts yet),
 * so there's no journal entry that would correspond to it.
 */
@Getter
@Setter
@Entity
public class StockTransfer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at transfer time, same convention as StockAdjustment.productName. */
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseLocation fromLocation;

    /** Snapshot — same reasoning as productName. */
    private String fromLocationName;

    /** Snapshot of fromLocation's own warehouse name at transfer time — lets the list screen show "Main / Main Stock" without an extra join once a location's warehouse later changes or is deleted. */
    private String fromWarehouseName;

    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseLocation toLocation;

    /** Snapshot — same reasoning as productName. */
    private String toLocationName;

    /** Snapshot — same reasoning as fromWarehouseName. */
    private String toWarehouseName;

    /** Always positive — direction is already encoded by fromLocation/toLocation, unlike StockAdjustment.quantityDelta which has no separate "direction" field. */
    private int quantity;

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    private StockTransferStatus status = StockTransferStatus.PENDING;

    /** Set only by {@code StockTransferService.confirm} — when the stock actually moved, as opposed to {@code getCreatedAt()} which is only when it was logged. */
    private java.time.Instant confirmedAt;

    /**
     * Fourier: inter-branch transfers — the branch of each end's warehouse, snapshotted when the
     * transfer is requested (null = head office / unknown, e.g. every transfer made before
     * branches existed). Two different values mean stock is moving between branches.
     */
    @jakarta.persistence.Column(name = "from_branch_id")
    private Long fromBranchId;

    private String fromBranchName;

    @jakarta.persistence.Column(name = "to_branch_id")
    private Long toBranchId;

    private String toBranchName;

    /** Inter-branch transfers: when the stock left the source. */
    @jakarta.persistence.Column(name = "dispatched_at")
    private java.time.Instant dispatchedAt;

    /** Inter-branch transfers: when the destination confirmed receipt, and how many units actually arrived. */
    @jakarta.persistence.Column(name = "received_at")
    private java.time.Instant receivedAt;

    @jakarta.persistence.Column(name = "received_quantity")
    private Integer receivedQuantity;

    @jakarta.persistence.Column(name = "receive_note", columnDefinition = "TEXT")
    private String receiveNote;

    /** Quantity at the source location just before / after the stock left it (set on same-branch confirm and on dispatch). Feeds Stock Movement History. */
    @jakarta.persistence.Column(name = "from_stock_before")
    private Integer fromStockBefore;

    @jakarta.persistence.Column(name = "from_stock_after")
    private Integer fromStockAfter;

    /** Quantity at the destination location just before / after the stock arrived (same-branch confirm, or receipt of an in-transit transfer). */
    @jakarta.persistence.Column(name = "to_stock_before")
    private Integer toStockBefore;

    @jakarta.persistence.Column(name = "to_stock_after")
    private Integer toStockAfter;

    /** Source location quantity before / after an in-transit transfer was returned to the sender. */
    @jakarta.persistence.Column(name = "return_stock_before")
    private Integer returnStockBefore;

    @jakarta.persistence.Column(name = "return_stock_after")
    private Integer returnStockAfter;
}
