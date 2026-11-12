package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A customer's account on the public shop. Identified by an e-mail address they proved they own; the rest is what they chose to keep. */
@Getter
@Setter
@Entity
public class ShopCustomer extends BaseEntity {
    /** Lower case. */
    private String email;
    private String phone;
    private String name;
    private String address;
    /** en or fr. */
    private String language = "en";
    private Instant lastLoginAt;
}
