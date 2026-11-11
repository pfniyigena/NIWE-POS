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
    /** PLACED, ACCEPTED, PACKED, OUT_FOR_DELIVERY, READY_FOR_PICKUP, COMPLETED, REJECTED or CANCELLED. */
    private String status = "PLACED";
    /** DELIVERY or PICKUP. */
    private String deliveryMethod;
    /** COD (cash on delivery or at pick-up) in phase 1. */
    private String paymentMethod = "COD";
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    private Long zoneId;
    private String zoneName;
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    private String notes;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    /** en or fr: the language of the messages sent to the customer. */
    private String language = "en";
    private Long customerId;
    private Long salesOrderId;
    private String salesOrderNumber;
    private Long invoiceId;
    private String invoiceNumber;
    private Instant decidedAt;
    private String decidedBy;
    private String rejectReason;

    @OneToMany(mappedBy = "onlineOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OnlineOrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "onlineOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<OnlineOrderEvent> events = new ArrayList<>();
}
