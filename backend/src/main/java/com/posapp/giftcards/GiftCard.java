package com.posapp.giftcards;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A gift card: a code and a balance the store owes the holder. The balance is a liability, not income, until it is spent (or released after the card expires). */
@Entity
@Table(name = "gift_card")
public class GiftCard extends BaseEntity {

    @Column(nullable = false)
    private String code;
    @Column(name = "initial_amount", nullable = false)
    private BigDecimal initialAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GiftCardStatus status = GiftCardStatus.ACTIVE;
    /** Who bought it (optional). */
    @Column(name = "customer_id")
    private Long customerId;
    @Column(name = "recipient_name")
    private String recipientName;
    /** Last day the card can be used (null = never expires). */
    @Column(name = "expires_on")
    private LocalDate expiresOn;
    private String note;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getInitialAmount() { return initialAmount; }
    public void setInitialAmount(BigDecimal initialAmount) { this.initialAmount = initialAmount; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public GiftCardStatus getStatus() { return status; }
    public void setStatus(GiftCardStatus status) { this.status = status; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isExpired(LocalDate today) {
        return expiresOn != null && today.isAfter(expiresOn);
    }

    /** Can it be spent right now? */
    public boolean isUsable(LocalDate today) {
        return status == GiftCardStatus.ACTIVE && !isExpired(today) && balance.signum() > 0;
    }
}
