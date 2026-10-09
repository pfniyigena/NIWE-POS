package com.posapp.auth;

import com.posapp.common.BaseEntity;
import com.posapp.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Fourier: "Add forget password feature where user will input the email and he/she can reset the
 * password" — one single-use, time-limited token per forgot-password request, emailed as a link
 * (see PasswordResetService.requestReset). Deliberately its own row per request rather than a
 * single "reset token" column on {@code AppUser}: keeping history (and letting more than one
 * outstanding request exist, e.g. a stale email finally clicked after a newer request was already
 * made) needs no special-casing this way, and {@code PasswordResetService.consumeToken} simply
 * disregards every other still-unused token for that user once one succeeds.
 */
@Getter
@Setter
@Entity
public class PasswordResetToken extends BaseEntity {

    @ManyToOne
    private AppUser user;

    /** A random, unguessable, URL-safe string — see PasswordResetService.generateToken. Looked up as-is, so it's effectively this row's business key even though {@code id} is still the JPA primary key. */
    private String token;

    private Instant expiresAt;

    private boolean used = false;
}
