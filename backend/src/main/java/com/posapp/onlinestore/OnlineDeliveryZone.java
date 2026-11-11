package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** An area the shop delivers to, with its fee and its own minimum order. */
@Getter
@Setter
@Entity
public class OnlineDeliveryZone extends BaseEntity {
    private String name;
    private BigDecimal fee = BigDecimal.ZERO;
    private BigDecimal minOrder = BigDecimal.ZERO;
    private boolean active = true;
    private int sortOrder = 0;
}
