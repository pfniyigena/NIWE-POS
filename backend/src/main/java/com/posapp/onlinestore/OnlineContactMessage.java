package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A message a visitor sent from the shop's contact form. NEW until staff mark it answered. */
@Getter
@Setter
@Entity
public class OnlineContactMessage extends BaseEntity {
    private String name;
    private String email;
    private String phone;
    private String subject;
    private String message;
    private String orderCode;
    private String language = "en";
    private String status = "NEW";
    private String note;
    private String handledBy;
    private Instant handledAt;
    /** The answer staff sent by e-mail from the Messages screen, and when. */
    private String reply;
    private Instant repliedAt;
    private String sourceIp;
}
