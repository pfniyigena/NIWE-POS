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
    /** REQUESTED, APPROVED, COLLECTED (a driver has the goods), REFUSED or REFUNDED. */
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

    /** v1.62.0: the driver who collects the goods from the customer (delivery orders only), and the return code the customer gives him. */
    private Long pickupDriverId;
    private String pickupDriverName;
    private String pickupDriverPhone;
    private String pickupCode;
    private int pickupFailures = 0;
    private Instant pickupFailedAt;
    /** When the driver collected the goods, and who (COLLECTED: waiting to be checked and refunded). */
    private Instant collectedAt;
    private String collectedBy;

    /** The return code, made when a return of a delivered order is approved. */
    public String ensurePickupCode() {
        if (pickupCode == null || pickupCode.isBlank()) {
            pickupCode = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
            pickupFailures = 0;
            pickupFailedAt = null;
        }
        return pickupCode;
    }

    /** A new return code, different from the one before; the wrong-code counter starts again. */
    public String reissuePickupCode() {
        String old = pickupCode;
        String fresh;
        do {
            fresh = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
        } while (fresh.equals(old));
        pickupCode = fresh;
        pickupFailures = 0;
        pickupFailedAt = null;
        return fresh;
    }

    @OneToMany(mappedBy = "onlineReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OnlineReturnLine> lines = new ArrayList<>();
}
