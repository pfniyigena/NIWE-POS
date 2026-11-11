package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** The shop's own switches and limits (one row). The public page answers "not available" until {@code enabled} is on. */
@Getter
@Setter
@Entity
public class OnlineStoreSettings extends BaseEntity {
    private boolean enabled = false;
    /** Temporarily stop taking orders; the page stays up and shows {@code pauseMessage}. */
    private boolean paused = false;
    private String pauseMessage;
    private String shopName;
    private String introText;
    private String contactPhone;
    private String pickupAddress;
    private boolean pickupEnabled = true;
    private boolean deliveryEnabled = true;
    /** Cash on delivery or at pick-up. Phase 1 has no other way to pay. */
    private boolean codEnabled = true;
    /** Units kept back from the shop for every product, so the last unit is not sold twice. */
    private int stockBuffer = 1;
    private BigDecimal minOrder = BigDecimal.ZERO;
    /** Delivery is free when the items come to at least this much; empty = never free. */
    private BigDecimal freeDeliveryOver;
    private int maxPendingPerPhone = 3;
    private int maxPerIpPerHour = 20;
    /** A service product used to put the delivery fee on the sale order and the invoice. Needed before any delivery order can be accepted. */
    private Long deliveryProductId;
}
