package com.posapp.inventory;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Task 1: "Inventory Report" (the "Stock movement history" half). One row per product per call to
 * {@link PurchaseOrderService#receiveLines} — the structured, per-product, per-event counterpart
 * to {@link StockAdjustment} that receiving previously didn't have: before this, the only record
 * of a receipt was {@code PurchaseOrderLine.quantityReceived} (a running cumulative total with no
 * per-event timestamp — can't tell "how much arrived on which date" once a line has been received
 * more than once) plus an AuditLog text description covering the whole receiving action, which can
 * bundle several products into one line of prose. This entity is what lets the movement-history
 * report show one clean dated row per product actually received, the same way {@link
 * StockAdjustment} already does for manual corrections. {@code productName}/{@code orderNumber}/
 * {@code unitCost} are snapshots at receiving time, same "don't rewrite history when something
 * later changes" convention as every other trail entity in this app (StockAdjustment,
 * PurchaseOrderLine). Who received it is, same as StockAdjustment, left to AuditLogService rather
 * than duplicated here. {@code stockBefore}/{@code stockAfter} (Task: "On Stock Movement History
 * ... add the previous quantity and current quantity after transaction") are the same before/after
 * snapshot convention {@link StockAdjustment} already uses, added here so the movement-history
 * report can show both event types' running stock the same way.
 */
@Getter
@Setter
@Entity
public class StockReceipt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseOrder purchaseOrder;

    /** Snapshot of PurchaseOrder.orderNumber at receiving time. */
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at receiving time, so a later rename doesn't rewrite this trail entry. */
    private String productName;

    private int quantityReceived;

    private int stockBefore;

    private int stockAfter;

    /** Snapshot of the PurchaseOrderLine's own unitCost for this delivery — see that field's javadoc for why it can differ from Product.costPrice. */
    private BigDecimal unitCost = BigDecimal.ZERO;
}
