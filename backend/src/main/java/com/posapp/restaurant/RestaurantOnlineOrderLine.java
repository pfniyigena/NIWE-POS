package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class RestaurantOnlineOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_order_id")
    private RestaurantOnlineOrder onlineOrder;
    private Long menuItemId;
    private String name;
    private int quantity = 1;
    private String notes;
    /** Option ids the guest picked, comma separated. */
    private String optionIds;
    /** The same options by name, for showing. */
    private String optionNames;
    /** Price for one, with the options, at the time the order was placed. */
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;
}
