package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A person who delivers the shop's orders. Not an ERP user or employee: a simple list kept in Shop settings. */
@Getter
@Setter
@Entity
public class OnlineDriver extends BaseEntity {
    private String name;
    private String phone;
    private String notes;
    private boolean active = true;

    /** v1.59.0: the secret in the driver's private link (null = no link). */
    private String accessToken;
    private java.time.Instant accessCreatedAt;
    private java.time.Instant accessLastUsedAt;
    /** The 4-digit PIN, hashed. */
    private String pinHash;
    private int pinFailures;
    private java.time.Instant pinFailedAt;
    /** SHA-256 of the session the driver got after the PIN; one session per driver. */
    private String sessionHash;
    private java.time.Instant sessionExpiresAt;
}
