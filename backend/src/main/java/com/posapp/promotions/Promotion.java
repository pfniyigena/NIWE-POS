package com.posapp.promotions;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promotion")
public class Promotion extends BaseEntity {

    @Column(nullable = false)
    private String name;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "promo_type", nullable = false)
    private PromotionType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromotionScope scope;
    @Column(name = "target_id")
    private Long targetId;
    private BigDecimal value;
    @Column(name = "buy_quantity")
    private BigDecimal buyQuantity;
    @Column(name = "free_quantity")
    private BigDecimal freeQuantity;
    @Column(name = "min_quantity", nullable = false)
    private BigDecimal minQuantity = BigDecimal.ONE;
    @Column(name = "min_spend")
    private BigDecimal minSpend;
    @Column(name = "valid_from")
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    @Column(name = "coupon_code")
    private String couponCode;
    @Column(name = "max_uses")
    private Integer maxUses;
    @Column(name = "max_uses_per_customer")
    private Integer maxUsesPerCustomer;
    @Column(name = "customer_group_id")
    private Long customerGroupId;
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }
    public PromotionScope getScope() { return scope; }
    public void setScope(PromotionScope scope) { this.scope = scope; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getBuyQuantity() { return buyQuantity; }
    public void setBuyQuantity(BigDecimal buyQuantity) { this.buyQuantity = buyQuantity; }
    public BigDecimal getFreeQuantity() { return freeQuantity; }
    public void setFreeQuantity(BigDecimal freeQuantity) { this.freeQuantity = freeQuantity; }
    public BigDecimal getMinQuantity() { return minQuantity; }
    public void setMinQuantity(BigDecimal minQuantity) { this.minQuantity = minQuantity; }
    public BigDecimal getMinSpend() { return minSpend; }
    public void setMinSpend(BigDecimal minSpend) { this.minSpend = minSpend; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public Integer getMaxUsesPerCustomer() { return maxUsesPerCustomer; }
    public void setMaxUsesPerCustomer(Integer maxUsesPerCustomer) { this.maxUsesPerCustomer = maxUsesPerCustomer; }
    public Long getCustomerGroupId() { return customerGroupId; }
    public void setCustomerGroupId(Long customerGroupId) { this.customerGroupId = customerGroupId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isInDates(LocalDate today) {
        return (validFrom == null || !today.isBefore(validFrom)) && (validTo == null || !today.isAfter(validTo));
    }
}
