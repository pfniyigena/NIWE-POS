package com.posapp.salesorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One product/quantity being returned against a {@link SalesOrderLine} that had already been
 * fulfilled — same "snapshot the refund amount at return time" reasoning as {@code
 * com.posapp.returns.SalesReturnLine.refundAmount}, so a later price change on the original line
 * never rewrites this return's own history.
 */
@Getter
@Setter
@Entity
public class SalesOrderReturnLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SalesOrderReturn salesOrderReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    private SalesOrderLine orderLine;

    private int quantity;

    /** {@code quantity * orderLine.unitPrice} at the moment of return — snapshotted, see class javadoc. */
    private BigDecimal refundAmount;

    /**
     * Task 6: "Any transaction affecting stock should be shown to Stock Movement History (...Sale
     * orders)" — this product's stock immediately before/after this return applied {@code
     * +quantity} to it (see {@code SalesOrderReturnService#processReturn}), same snapshot
     * reasoning as {@code com.posapp.order.OrderLine.stockBefore}/{@code stockAfter}.
     */
    private Integer stockBefore;
    private Integer stockAfter;
}
