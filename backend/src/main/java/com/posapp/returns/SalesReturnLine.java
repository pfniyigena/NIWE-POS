package com.posapp.returns;

import com.posapp.common.BaseEntity;
import com.posapp.order.OrderLine;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class SalesReturnLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SalesReturn salesReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    private OrderLine orderLine;

    private int quantity;

    /**
     * Snapshot of what this one line was refunded (post-discount, pre-tax-extraction — the same
     * `lineRefund` figure ReturnsService.processReturn already computes to build the return's
     * overall subtotal/tax split), so a return receipt can show a per-line amount without
     * recomputing OrderLine pricing/tax logic a second time somewhere else. Task: "print out the
     * return". Nullable — null for any return processed before this field existed; the return
     * receipt falls back to showing no amount for those rather than a wrong recomputed one.
     */
    private BigDecimal refundAmount;

    /**
     * Task 6: "Any transaction affecting stock should be shown to Stock Movement History (...Pos
     * sales)" — widened to also cover POS returns. This product's stock immediately before/after
     * this return applied {@code +quantity} to it (see {@code ReturnsService#processReturn}),
     * snapshotted the same way {@code OrderLine.stockBefore}/{@code stockAfter} are. Both null for
     * a line with no product, or one processed before this field existed.
     */
    private Integer stockBefore;
    private Integer stockAfter;
}
