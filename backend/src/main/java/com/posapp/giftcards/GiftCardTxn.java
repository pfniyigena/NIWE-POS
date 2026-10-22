package com.posapp.giftcards;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One movement on a gift card; the card's history is the list of these. */
@Entity
@Table(name = "gift_card_txn")
public class GiftCardTxn extends BaseEntity {

    @Column(name = "gift_card_id", nullable = false)
    private Long giftCardId;
    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false)
    private GiftCardTxnType type;
    /** Signed: money onto the card is positive, money off it is negative. */
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;
    /** For a sale or top-up: how it was paid (a payment method code, or COMPLIMENTARY when given free). */
    @Column(name = "payment_method")
    private String paymentMethod;
    @Column(name = "register_session_id")
    private Long registerSessionId;
    @Column(name = "cashier_session_id")
    private Long cashierSessionId;
    @Column(name = "order_id")
    private Long orderId;
    private String note;

    public Long getGiftCardId() { return giftCardId; }
    public void setGiftCardId(Long giftCardId) { this.giftCardId = giftCardId; }
    public GiftCardTxnType getType() { return type; }
    public void setType(GiftCardTxnType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public Long getRegisterSessionId() { return registerSessionId; }
    public void setRegisterSessionId(Long registerSessionId) { this.registerSessionId = registerSessionId; }
    public Long getCashierSessionId() { return cashierSessionId; }
    public void setCashierSessionId(Long cashierSessionId) { this.cashierSessionId = cashierSessionId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
