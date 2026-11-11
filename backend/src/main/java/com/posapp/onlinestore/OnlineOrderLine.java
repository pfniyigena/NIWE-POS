package com.posapp.onlinestore;

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
public class OnlineOrderLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_order_id", nullable = false)
    private OnlineOrder onlineOrder;
    private Long productId;
    private String name;
    private BigDecimal quantity = BigDecimal.ONE;
    /** The price before any tax the customer sees on top; this is what goes on the sale order. */
    private BigDecimal basePrice = BigDecimal.ZERO;
    /** What the customer was shown per unit (tax included). */
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;
}
