package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer's request to return goods from a completed online order. REQUESTED until staff decide; APPROVED (come and bring the goods back) or REFUSED;
 * REFUNDED once the goods are back in stock through a sale order return and the money has been given back.
 */
@Getter
@Setter
@Entity
public class OnlineReturn extends BaseEntity {
    /** The order's code with -R1, -R2 ... what the customer and staff quote. */
    private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_order_id", nullable = false)
    private OnlineOrder onlineOrder;
    /** REQUESTED, APPROVED, REFUSED or REFUNDED. */
    private String status = "REQUESTED";
    private String reason;
    /** What staff wrote when they decided (the reason of a refusal, or where to bring the goods). */
    private String staffNote;
    private Instant decidedAt;
    private String decidedBy;
    /** What the customer paid for the goods being returned (the delivery fee is not given back). */
    private BigDecimal refundAmount = BigDecimal.ZERO;
    /** PROVIDER (the payment provider refunded), MANUAL (staff refunded outside, with a reference) or CASH (staff handed cash back). */
    private String refundMethod;
    private String refundReference;
    private Instant refundedAt;
    private String refundedBy;
    private Long salesOrderReturnId;
    private String salesOrderReturnNumber;
    private Long locationId;

    @OneToMany(mappedBy = "onlineReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OnlineReturnLine> lines = new ArrayList<>();
}
