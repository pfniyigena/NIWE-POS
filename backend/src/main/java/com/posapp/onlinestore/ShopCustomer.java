package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A customer's account on the public shop. Identified by an e-mail address, or a phone number, that they proved they own; the rest is what they chose to keep. */
@Getter
@Setter
@Entity
public class ShopCustomer extends BaseEntity {
    /** Lower case. Empty for an account opened with a phone number only. */
    private String email;
    private String phone;
    /** Last 9 digits of the phone. */
    private String phoneKey;
    /** The customer proved the phone with a text message: it can then be used to sign in. At most one account holds a verified number. */
    private boolean phoneVerified;
    private String name;
    private String address;
    /** en or fr. */
    private String language = "en";
    private Instant lastLoginAt;
    /** Loyalty points: the balance (every change is a row of loyalty_entry). */
    private int loyaltyPoints;
}
