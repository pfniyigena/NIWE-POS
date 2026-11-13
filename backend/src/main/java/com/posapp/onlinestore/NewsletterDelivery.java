package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One address of one campaign. PENDING, SENDING (taken, being handed to the mail server), SENT, FAILED or SKIPPED (the person left before their turn). */
@Getter
@Setter
@Entity
public class NewsletterDelivery extends BaseEntity {
    private Long campaignId;
    private Long subscriberId;
    private String email;
    private String language = "en";
    private String status = "PENDING";
    private String error;
    private Instant sentAt;
}
