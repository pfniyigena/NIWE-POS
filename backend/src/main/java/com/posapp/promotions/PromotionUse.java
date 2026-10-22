package com.posapp.promotions;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One promotion applied to one completed sale, and how much it took off. */
@Entity
@Table(name = "promotion_use")
public class PromotionUse extends BaseEntity {

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "customer_id")
    private Long customerId;
    @Column(nullable = false)
    private BigDecimal amount;

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
