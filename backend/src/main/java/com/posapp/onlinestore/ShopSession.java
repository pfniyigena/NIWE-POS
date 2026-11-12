package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A browser that is signed in to a shop account. Only a hash of its token is stored. */
@Getter
@Setter
@Entity
public class ShopSession extends BaseEntity {
    private Long shopCustomerId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant lastUsedAt;
}
