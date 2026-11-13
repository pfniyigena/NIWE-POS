package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One newsletter: the message and who it goes to. DRAFT is editable; SENDING is being sent in batches; SENT has nothing left to send; CANCELLED was stopped. */
@Getter
@Setter
@Entity
public class NewsletterCampaign extends BaseEntity {
    private String subject;
    @Column(columnDefinition = "text")
    private String body;
    private String buttonLabel;
    private String buttonLink;
    /** ALL, EN or FR (the language each subscriber chose when signing up). */
    private String audience = "ALL";
    private String status = "DRAFT";
    private int recipients;
    private String createdBy;
    private Instant startedAt;
    private Instant finishedAt;
    private String lastError;
}
