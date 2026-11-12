package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A one-time sign-in code that was sent. Only its hash is stored. */
@Getter
@Setter
@Entity
public class ShopLoginCode extends BaseEntity {
    /** The e-mail address, or the phone number in international form, the code was sent to. */
    private String identifier;
    private String codeHash;
    private Instant expiresAt;
    private int attempts;
    private boolean used;
}
