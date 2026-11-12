package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** An e-mail address that asked for the shop's newsletter. Only CONFIRMED ones (the link in the e-mail was opened) are counted as subscribers. */
@Getter
@Setter
@Entity
public class NewsletterSubscriber extends BaseEntity {
    private String email;
    private String language = "en";
    /** PENDING, CONFIRMED or UNSUBSCRIBED. */
    private String status = "PENDING";
    /** SHA-256 of the confirmation token; the token itself is only in the e-mail. */
    private String confirmHash;
    private String unsubscribeToken;
    private Instant confirmSentAt;
    private Instant confirmedAt;
    private Instant unsubscribedAt;
    private String sourceIp;
}
