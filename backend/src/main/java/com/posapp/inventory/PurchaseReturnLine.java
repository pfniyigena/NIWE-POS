package com.posapp.inventory;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One product/quantity being sent back to the supplier against a {@link PurchaseOrderLine} that
 * had already been received — same "snapshot the refund amount at return time" reasoning as
 * {@code com.posapp.salesorder.SalesOrderReturnLine.refundAmount}.
 */
@Getter
@Setter
@Entity
public class PurchaseReturnLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseReturn purchaseReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseOrderLine orderLine;

    private int quantity;

    /** {@code quantity * orderLine.unitCost} at the moment of return — snapshotted, see class javadoc. */
    private BigDecimal refundAmount;

    /**
     * Task 6: "Any transaction affecting stock should be shown to Stock Movement History
     * (...Purchases orders)" — this product's stock immediately before/after this return applied
     * {@code -quantity} to it (see {@code PurchaseReturnService#processReturn}), same snapshot
     * reasoning as {@code com.posapp.order.OrderLine.stockBefore}/{@code stockAfter}.
     */
    private Integer stockBefore;
    private Integer stockAfter;
}
