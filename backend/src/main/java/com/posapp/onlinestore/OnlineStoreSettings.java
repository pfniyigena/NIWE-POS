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
    /** The shop's WhatsApp number (any way of writing it). Empty = the phone shown on the shop is used. */
    private String whatsappNumber;
    /** Told by e-mail when a customer places an order. Empty = the business e-mail from Settings. */
    private String notifyEmail;
    /** An order that waits this many minutes as PLACED is reported to the shop by e-mail, once. 0 = never. */
    private int unansweredAlertMinutes = 30;
    /** Customers may pay at checkout through the payment provider below (phase 2). */
    /** Customers may sign in with an e-mailed code to see their orders and keep their address (phase 3). */
    private boolean accountsEnabled = false;
    /** A visitor who typed an e-mail address at checkout and left gets two reminders about the cart (1 hour and 3 days later), no discount. Off until the shop switches it on. */
    private boolean cartRemindersEnabled = false;
    /** Signed-in customers earn loyalty points on completed orders and spend them at checkout (1 point = 1 unit of the currency). Off until switched on. */
    private boolean loyaltyEnabled = false;
    /** Points earned per 100 of the currency paid for the goods. */
    private int loyaltyPointsPer100 = 1;
    /** Points may pay at most this share (percent) of what is left to pay for the goods. */
    private int loyaltyMaxPercent = 50;
    private boolean onlinePaymentsEnabled = false;
    /** Text messages (phase 3): the code of the text provider (see TextProvider), e.g. SIMULATED while testing. Empty = texting is off. */
    private String textProvider;
    /** SMS or WHATSAPP: how texts are delivered. */
    private String textChannel = "SMS";
    /** Put in front of local numbers (0788...) to make them international: 250 for Rwanda. */
    private String textCountryCode = "250";
    /** At most this many texts are sent in 24 hours (0 = no limit). Keeps the cost, and any abuse, bounded. */
    private int textDailyLimit = 300;
    /** The owner's phone: texted about new orders and orders waiting too long. Empty = nobody is texted. */
    private String notifyPhone;
    /** A customer may ask to return goods up to this many days after the order was completed. 0 = no returns through the shop. */
    private int returnWindowDays = 14;
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
    /** v1.66.0: an order nobody answered (cash orders waiting for staff, orders waiting for a payment) is cancelled after this many hours and its stock freed. 0 = never. */
    @jakarta.persistence.Column(name = "auto_release_hours", nullable = false)
    private int autoReleaseHours = 24;
    /** v1.67.0: batches with fewer days left than this before their expiry date are not sold by the shop (0 = only expired batches are held back). */
    @jakarta.persistence.Column(name = "min_days_left", nullable = false)
    private int minDaysLeft = 0;
    /** v1.66.0: percent of the available units of each product kept for the till, on top of the buffer (rounded up). 0 = none. */
    @jakarta.persistence.Column(name = "till_share_percent", nullable = false)
    private int tillSharePercent = 0;
    private BigDecimal minOrder = BigDecimal.ZERO;
    /** Delivery is free when the items come to at least this much; empty = never free. */
    private BigDecimal freeDeliveryOver;
    private int maxPendingPerPhone = 3;
    private int maxPerIpPerHour = 20;
    /** A service product used to put the delivery fee on the sale order and the invoice. Needed before any delivery order can be accepted. */
    private Long deliveryProductId;
    /** v1.65.0: with multi-warehouse on, the Sales location the shop sells from; null = every Sales location outside the Main warehouse. */
    private Long shopLocationId;
    /** The front page's wording (v1.35.0). Each is optional; a part with nothing set is not shown. */
    private String announcementText;
    private String announcementLink;
    private String heroTitle;
    private String heroSubtitle;
    private String heroButtonText;
    private String heroButtonLink;
    private String heroImageUrl;
    private String aboutText;
    private String contactEmail;
    private String openingHours;
    /** One link per line, written "Label|address". */
    private String footerLinks;
    private String socialLinks;
    /** The shop's colour (#rrggbb) for buttons and links; empty = the standard colour. */
    private String brandColor;
    /** Estimated delivery, in days after the order is accepted (both empty = no estimate shown). */
    private Integer deliveryDaysMin;
    private Integer deliveryDaysMax;
    /** Product rows (Featured, Best sellers, ...) slide by themselves; off = the visitor slides them with the arrows or a swipe. */
    @jakarta.persistence.Column(name = "auto_slide_rows", nullable = false)
    private boolean autoSlideRows = false;
    /** v1.61.0: customers choose a delivery window at checkout. */
    @jakarta.persistence.Column(name = "slots_enabled", nullable = false)
    private boolean slotsEnabled = false;
    /** The window must be chosen (off = "as soon as possible" is allowed). */
    @jakarta.persistence.Column(name = "slots_required", nullable = false)
    private boolean slotsRequired = false;
    /** How many days after today a window can be booked. */
    @jakarta.persistence.Column(name = "slot_days_ahead", nullable = false)
    private int slotDaysAhead = 7;
    /** A window can be booked until this many hours before it starts. */
    @jakarta.persistence.Column(name = "slot_notice_hours", nullable = false)
    private int slotNoticeHours = 2;
    /** v1.69.0: the price list (Sales > Price lists) whose prices the shop uses for products without an online price. Empty = none. */
    @jakarta.persistence.Column(name = "price_list_id")
    private Long priceListId;
    /** v1.74.0: the branch the online shop sells for: its sale orders, invoices and payments carry it. Empty = head office. */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
