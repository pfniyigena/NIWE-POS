package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Some quantity of one order line that the customer sends back, with what it is worth to them (after any coupon). */
@Getter
@Setter
@Entity
public class OnlineReturnLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_return_id", nullable = false)
    private OnlineReturn onlineReturn;
    private Long onlineOrderLineId;
    private Long productId;
    private String name;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal amount = BigDecimal.ZERO;
}
