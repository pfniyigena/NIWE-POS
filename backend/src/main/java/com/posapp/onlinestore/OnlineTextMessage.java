package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** One text message the shop sent, or tried to, or skipped (over the daily limit, bad number). Sign-in codes are logged without the code. */
@Getter
@Setter
@Entity
public class OnlineTextMessage extends BaseEntity {
    /** SMS or WHATSAPP. */
    private String channel;
    /** International form, +250788123456. */
    private String toPhone;
    /** ORDER, OWNER, SIGNIN or TEST. */
    private String purpose;
    private String body;
    /** SENT, FAILED or SKIPPED. */
    private String status;
    private String provider;
    private String providerRef;
    private String error;
    private Long onlineOrderId;
}
