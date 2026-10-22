package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** What was picked for an ordered line, copied at order time so a later menu edit never rewrites the bill. */
@Getter
@Setter
@Entity
public class RestaurantOrderLineOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RestaurantOrderLine line;

    private Long optionId;
    private String groupName;
    private String optionName;
    private BigDecimal priceDelta = BigDecimal.ZERO;
}
