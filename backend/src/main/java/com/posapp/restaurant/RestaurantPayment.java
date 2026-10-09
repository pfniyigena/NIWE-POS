package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One tender against a {@link RestaurantOrder}'s bill — a list of these, not a single amount, is
 * what makes split billing possible (Fourier: "Include split & merge bills"): a table can pay
 * with more than one method, or more than one guest can each cover part of the bill.
 */
@Getter
@Setter
@Entity
public class RestaurantPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RestaurantOrder order;

    /** Same code-not-foreign-key snapshot convention as {@code com.posapp.order.Payment.method} — a later rename/deactivation of the payment method never rewrites an already-collected payment. */
    private String method;

    private BigDecimal amount;
}
