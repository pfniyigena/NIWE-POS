package com.posapp.salesorder;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * Task 6: "Any transaction affecting stock should be shown to Stock Movement History (...Sale
 * orders)." One row per product per call to {@link SalesOrderService#fulfillLines} — the
 * structured, per-product, per-event counterpart to {@code com.posapp.inventory.StockReceipt} on
 * the purchasing side, which fulfillment previously didn't have: before this, the only record of
 * a fulfillment was {@code SalesOrderLine.quantityFulfilled} (a running cumulative total with no
 * per-event timestamp) plus an AuditLog text description covering the whole fulfillment action.
 * This is what lets {@code ReportService.stockMovementHistory} show one clean dated row per
 * product actually fulfilled, the same way {@code StockReceipt} does for receiving. {@code
 * orderNumber}/{@code productName} are snapshots at fulfillment time, same "don't rewrite history
 * when something later changes" convention as every other trail entity in this app. {@code
 * createdAt} (inherited from {@link BaseEntity}) is this event's own timestamp — deliberately not
 * a custom field, since {@code StockReceipt}/{@code StockAdjustment} already read that same way.
 */
@Getter
@Setter
@Entity
public class SalesOrderFulfillment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SalesOrder salesOrder;

    /** Snapshot of SalesOrder.orderNumber at fulfillment time. */
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at fulfillment time, so a later rename doesn't rewrite this trail entry. */
    private String productName;

    private int quantityFulfilled;

    private int stockBefore;

    private int stockAfter;

    /**
     * Fourier: multi-warehouse phase 3 — the location this fulfilment moved stock at. Nullable: null
     * means the legacy behaviour (the product's plain primary-warehouse stock figure), which is
     * what every row created before this column existed did. {@code locationName}/{@code
     * warehouseName} are snapshots so renaming either later doesn't rewrite the trail.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private com.posapp.warehouse.WarehouseLocation location;

    private String locationName;

    private String warehouseName;
}
