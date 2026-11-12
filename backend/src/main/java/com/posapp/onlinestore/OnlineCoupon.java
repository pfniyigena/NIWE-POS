package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A code the customer types at checkout. type is PERCENT, FIXED or FREE_DELIVERY; the dates are inclusive. */
@Getter
@Setter
@Entity
public class OnlineCoupon extends BaseEntity {
    /** Always upper case. */
    private String code;
    private String description;
    private String type = "PERCENT";
    /** Percent (PERCENT) or amount (FIXED); not used by FREE_DELIVERY. */
    private BigDecimal value = BigDecimal.ZERO;
    /** PERCENT only: the discount never goes above this amount. */
    private BigDecimal maxDiscount;
    private BigDecimal minOrder = BigDecimal.ZERO;
    private LocalDate validFrom;
    private LocalDate validUntil;
    /** Orders that may use it in all; null = no limit. */
    private Integer maxUses;
    private boolean oncePerCustomer = true;
    private boolean active = true;
}
