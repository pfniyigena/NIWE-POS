package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One try to pay an online order. An order can have several (a declined mobile money payment, then a card), at most one of them ends PAID. */
@Getter
@Setter
@Entity
public class OnlinePayment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_order_id", nullable = false)
    private OnlineOrder onlineOrder;
    private String provider;
    /** Ours, unique; what the provider sends back to tell us which payment this is. */
    private String reference;
    private String providerReference;
    /** MOBILE_MONEY or CARD. */
    private String method;
    private String phone;
    private BigDecimal amount;
    private String currency;
    /** PENDING, PAID, FAILED, EXPIRED or REVIEW (money arrived that needs a person to look at it). */
    private String status = "PENDING";
    private String failureReason;
    private String redirectUrl;
    private Instant paidAt;
    private Instant lastCheckedAt;
    private Instant expiresAt;
}
