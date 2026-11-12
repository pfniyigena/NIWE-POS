package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** What the provider told us, kept as received (only calls with a valid signature). Evidence when a customer and the shop disagree. */
@Getter
@Setter
@Entity
public class OnlinePaymentWebhook extends BaseEntity {
    private String provider;
    private String reference;
    /** What we did with it: PAID, FAILED, IGNORED, DUPLICATE, REVIEW, UNKNOWN_REFERENCE, BAD_BODY. */
    private String outcome;
    @Column(columnDefinition = "text")
    private String body;
    private String sourceIp;
}
