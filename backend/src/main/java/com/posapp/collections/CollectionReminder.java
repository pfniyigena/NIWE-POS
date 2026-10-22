package com.posapp.collections;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One e-mail sent to a customer about what they owe: a reminder (by hand or from the daily run) or a plain statement. */
@Entity
@Table(name = "collection_reminder")
public class CollectionReminder extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** MANUAL, AUTO or STATEMENT. */
    @Column(nullable = false, length = 12)
    private String kind;

    @Column(name = "to_email")
    private String toEmail;

    @Column(nullable = false)
    private int items;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "sent_by")
    private String sentBy;

    public Long getCustomerId() { return this.customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getKind() { return this.kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getToEmail() { return this.toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }
    public int getItems() { return this.items; }
    public void setItems(int items) { this.items = items; }
    public BigDecimal getAmount() { return this.amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getSentBy() { return this.sentBy; }
    public void setSentBy(String sentBy) { this.sentBy = sentBy; }
}
