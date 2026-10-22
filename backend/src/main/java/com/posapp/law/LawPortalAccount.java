package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A person at a client who may sign in to the client portal. It is a separate kind of login from the firm's own users: it can reach only the
 * portal, and only the matters of its own client (customer). The password is set by the person through a one-time invitation link.
 */
@Getter
@Setter
@Entity
@Table(indexes = @Index(columnList = "customer_id"), uniqueConstraints = @UniqueConstraint(name = "uq_law_portal_account_email", columnNames = "email"))
public class LawPortalAccount extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String name;

    /** Always lower case; this is what the person signs in with. */
    @Column(nullable = false)
    private String email;

    /** Empty until the person has accepted the invitation. */
    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    /** SHA-256 of the one-time invitation / reset token (the token itself is never stored). */
    @Column(length = 64)
    private String inviteTokenHash;

    private Instant inviteExpiresAt;
    private Instant lastLoginAt;

    @Column(nullable = false)
    private int failedAttempts = 0;

    private Instant lockedUntil;

    /** Sign-ins made before this moment stop working (set when the password changes or the account is switched off). */
    private Instant sessionsValidFrom;

    private String createdByName;
}
