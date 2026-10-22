package com.posapp.loyalty;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One movement of a customer's points; the balance is the sum of these. */
@Entity
@Table(name = "loyalty_txn")
public class LoyaltyTxn extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false)
    private LoyaltyTxnType type;
    /** Signed: points gained are positive, points lost are negative. */
    @Column(nullable = false)
    private int points;
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "return_id")
    private Long returnId;
    private String note;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LoyaltyTxnType getType() { return type; }
    public void setType(LoyaltyTxnType type) { this.type = type; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public int getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(int balanceAfter) { this.balanceAfter = balanceAfter; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getReturnId() { return returnId; }
    public void setReturnId(Long returnId) { this.returnId = returnId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
