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
    /** v1.59.1: the tax (from the existing tax rates) that the fee carries; null = the delivery product's own tax. The fee is the amount the customer pays, tax included. */
    private Long taxRateId;
}
