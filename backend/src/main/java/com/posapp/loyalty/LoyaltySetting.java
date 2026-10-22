package com.posapp.loyalty;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** The shop's one loyalty programme (row id 1): how points are earned and what they are worth. */
@Entity
@Table(name = "loyalty_setting")
public class LoyaltySetting extends BaseEntity {

    @Column(nullable = false)
    private boolean enabled = false;
    /** Every this much spent earns {@link #pointsEarned} points. */
    @Column(name = "spend_amount", nullable = false)
    private BigDecimal spendAmount = new BigDecimal("1000");
    @Column(name = "points_earned", nullable = false)
    private int pointsEarned = 1;
    /** What one point is worth when spent. */
    @Column(name = "point_value", nullable = false)
    private BigDecimal pointValue = new BigDecimal("10");
    @Column(name = "min_redeem_points", nullable = false)
    private int minRedeemPoints = 0;
    /** Points may pay at most this share of one sale. */
    @Column(name = "max_redeem_percent", nullable = false)
    private BigDecimal maxRedeemPercent = new BigDecimal("50");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getSpendAmount() { return spendAmount; }
    public void setSpendAmount(BigDecimal spendAmount) { this.spendAmount = spendAmount; }
    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
    public BigDecimal getPointValue() { return pointValue; }
    public void setPointValue(BigDecimal pointValue) { this.pointValue = pointValue; }
    public int getMinRedeemPoints() { return minRedeemPoints; }
    public void setMinRedeemPoints(int minRedeemPoints) { this.minRedeemPoints = minRedeemPoints; }
    public BigDecimal getMaxRedeemPercent() { return maxRedeemPercent; }
    public void setMaxRedeemPercent(BigDecimal maxRedeemPercent) { this.maxRedeemPercent = maxRedeemPercent; }
}
