package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A customer waiting for a sold-out product (an option of a product with options is a product of its own). WAITING until the shop has told them
 * (NOTIFIED, by the automatic e-mail / text or by staff), FAILED when the automatic message could not be sent three times (staff can still write to them).
 */
@Getter
@Setter
@Entity
public class OnlineStockAlert extends BaseEntity {
    private Long productId;
    private String productName;
    private String email;
    private String phone;
    private String language = "en";
    private String status = "WAITING";
    private int attempts;
    private Instant notifiedAt;
    private String notifiedBy;
    private String sourceIp;
}
