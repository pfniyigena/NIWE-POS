package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One thing the guest can pick inside an option group, with the price it adds (or takes off) per unit. */
@Getter
@Setter
@Entity
public class RestaurantOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RestaurantOptionGroup group;

    private String name;
    private BigDecimal priceDelta = BigDecimal.ZERO;
    private int sortOrder = 0;
    private boolean active = true;
}
