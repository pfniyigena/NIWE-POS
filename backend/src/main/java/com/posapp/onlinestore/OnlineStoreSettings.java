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
    /** Told by e-mail when a customer places an order. Empty = the business e-mail from Settings. */
    private String notifyEmail;
    /** An order that waits this many minutes as PLACED is reported to the shop by e-mail, once. 0 = never. */
    private int unansweredAlertMinutes = 30;
    /** Customers may pay at checkout through the payment provider below (phase 2). */
    /** Customers may sign in with an e-mailed code to see their orders and keep their address (phase 3). */
    private boolean accountsEnabled = false;
    private boolean onlinePaymentsEnabled = false;
    /** The code of the payment provider (see PaymentProvider), e.g. SIMULATED while testing. */
    private String paymentProvider;
    /** A try to pay that is not confirmed after this many minutes is closed. */
    private int paymentExpiryMinutes = 30;
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
