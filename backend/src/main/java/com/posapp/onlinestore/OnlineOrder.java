package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An order a customer placed on the public shop. It waits as PLACED until staff accept it; accepting creates a normal confirmed sale order
 * (which holds the stock), and completing it hands the goods over, issues the invoice and, for cash, records the payment.
 */
@Getter
@Setter
@Entity
public class OnlineOrder extends BaseEntity {
    /** What the customer quotes and tracks the order with. */
    private String code;
    /** Loyalty points spent on this order, what they were worth (part of discountAmount) and the points this order earned when it was completed. */
    private int loyaltyPointsUsed;
    private java.math.BigDecimal loyaltyDiscount = java.math.BigDecimal.ZERO;
    private int loyaltyPointsEarned;
    /** When the shop was told by e-mail that this order is waiting for an answer (once per order). */
    private Instant alertedAt;
    /** AWAITING_PAYMENT (online payment not confirmed yet), PLACED, ACCEPTED, PACKED, OUT_FOR_DELIVERY, READY_FOR_PICKUP, COMPLETED, REJECTED or CANCELLED. */
    private String status = "PLACED";
    /** DELIVERY or PICKUP. */
    private String deliveryMethod;
    /** COD (cash on delivery or at pick-up) or ONLINE (paid at checkout through the payment provider). */
    private String paymentMethod = "COD";
    /** NONE (cash), AWAITING, FAILED, PAID, REFUND_DUE or REFUNDED. */
    private String paymentStatus = "NONE";
    /** MOBILE_MONEY or CARD, once paid. */
    private String paymentChannel;
    /** The provider's reference of the payment that was accepted. */
    private String paymentReference;
    private BigDecimal paidAmount;
    private Instant paidAt;
    private Instant refundedAt;
    private String refundReference;
    /** How much of the money paid online has been given back so far (the whole of it, or part of it for returned goods). */
    private BigDecimal refundedTotal = BigDecimal.ZERO;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    private Long zoneId;
    private String zoneName;
    /** v1.59.1: the tax of the delivery fee as it was when the order was placed (null = the delivery product's own tax). */
    private Long deliveryTaxRateId;
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    private String notes;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    /** en or fr: the language of the messages sent to the customer. */
    private String language = "en";
    private Long customerId;
    /** The shop account the order belongs to (a customer who was signed in, or who verified the same e-mail address later). */
    private Long shopCustomerId;
    /** The coupon used (the code is kept as typed on the order, even if the coupon is deleted later). */
    private Long couponId;
    private String couponCode;
    /** The customer's phone number reduced to its last 9 digits, to count "once per customer" whatever way the number is written. */
    private String couponKey;
    /** The customer ticked "send me updates by text" at checkout. Nothing is texted to anyone who did not. */
    private boolean textOptIn;
    /** Last 9 digits of the phone: 0788 123 456 and +250 788 123 456 are the same. */
    private String phoneKey;
    /** What the coupon took off the items; subtotal is before it, total after it. */
    private BigDecimal discountAmount = BigDecimal.ZERO;
    /** The delivery fee a free-delivery coupon waived (deliveryFee is what the customer pays). */
    private BigDecimal feeWaived = BigDecimal.ZERO;
    private Long salesOrderId;
    private String salesOrderNumber;
    private Long invoiceId;
    private String invoiceNumber;
    private Instant decidedAt;
    private String decidedBy;
    private String rejectReason;
    /** The driver chosen for a delivery order (name and phone are copies, kept for the slip and the history). */
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private Instant driverAssignedAt;

    /** The 4-digit code the customer gives to the driver (or the counter) when the goods are in their hands. Made when the order goes out or is ready; never shown to staff. */
    private String receiptCode;
    /** Wrong codes typed since the last good one; after five the code is blocked for half an hour. */
    private int receiptFailures;
    private Instant receiptFailedAt;
    /** When and how the goods were confirmed received: CUSTOMER (the customer tapped it), CODE (the code was typed) or DRIVER (the customer signed on the driver's phone). */
    private Instant receivedAt;
    private String receivedVia;
    /** Who confirmed it: "customer", or the staff user whose screen was used. */
    private String receivedBy;
    private String signedName;
    /** The drawn signature as a PNG data URL (data:image/png;base64,...). */
    @jakarta.persistence.Column(columnDefinition = "text")
    private String signature;

    /**
     * Cash recorded when a delivery order was completed (the invoice total paid in cash on delivery): what the driver must hand in. Null when nothing
     * was collected in cash (paid online, pick-up, another way to pay). Settled by staff: what was handed in, the difference and who/when.
     */
    private BigDecimal cashCollected;
    private Instant cashSettledAt;
    private String cashSettledBy;
    private BigDecimal cashHandedIn;
    private BigDecimal cashDifference;
    private String cashNote;

    /** How many times the driver came and could not hand the goods over (customer absent, refused, wrong address). Each puts the order back to PACKED. */
    private int deliveryAttempts;
    private String lastFailureReason;
    private Instant lastFailureAt;
    /** Set when the customer kept only part of the goods: the invoice total of what was delivered. Null when everything was delivered. */
    private BigDecimal deliveredTotal;
    /** For an order paid online and delivered in part: what is still to give back to the customer (paid minus invoiced). Null or zero when nothing is due. */
    private BigDecimal refundDifference;

    /** The delivery code, made on first use (orders that went out before v1.56.0 get theirs when the customer or the shop first needs it). */
    public String ensureReceiptCode() {
        if (receiptCode == null || receiptCode.isBlank()) {
            receiptCode = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
            receiptFailures = 0;
            receiptFailedAt = null;
        }
        return receiptCode;
    }

    /** A new delivery code, different from the one before; the wrong-code counter starts again (staff re-issue it, v1.58.1). */
    public String reissueReceiptCode() {
        String old = receiptCode;
        String fresh;
        do {
            fresh = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
        } while (fresh.equals(old));
        receiptCode = fresh;
        receiptFailures = 0;
        receiptFailedAt = null;
        return fresh;
    }

    @OneToMany(mappedBy = "onlineOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OnlineOrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "onlineOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<OnlineOrderEvent> events = new ArrayList<>();
}
