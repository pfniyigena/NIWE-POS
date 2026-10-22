package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** An area the restaurant delivers to, with its fee, its own minimum order and how much longer it takes. */
@Getter
@Setter
@Entity
public class RestaurantDeliveryZone extends BaseEntity {
    private String name;
    private BigDecimal fee = BigDecimal.ZERO;
    private BigDecimal minOrder = BigDecimal.ZERO;
    private int extraMinutes = 0;
    private boolean active = true;
    private int sortOrder = 0;
}
