package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** What one hour of a user's time costs the firm. Used only by the matter profitability report; one row per user, no row means none set. */
@Entity
@Table(name = "law_cost_rate", uniqueConstraints = @UniqueConstraint(name = "uq_law_cost_rate_user", columnNames = "user_id"))
public class LawCostRate extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hourly_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal hourlyCost = BigDecimal.ZERO;

    public Long getUserId() { return this.userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getHourlyCost() { return this.hourlyCost; }
    public void setHourlyCost(BigDecimal hourlyCost) { this.hourlyCost = hourlyCost; }
}
