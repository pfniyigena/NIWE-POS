package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The sale of one property to one buyer: from the offer, through the reservation and the sale agreement, to completion. */
@Getter
@Setter
@Entity
@Table(name = "re_deal", indexes = {@Index(columnList = "property_id"), @Index(columnList = "stage"), @Index(columnList = "agent_user_id")})
public class ReDeal extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    private Long leadId;

    /** The buyer as a customer: needed from the reservation on, because invoices go to a customer. */
    private Long buyerCustomerId;

    @Column(nullable = false, length = 200)
    private String buyerName;

    @Column(length = 40)
    private String buyerPhone;

    private String buyerEmail;

    @Column(name = "agent_user_id")
    private Long agentUserId;
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStage stage = DealStage.OFFER;

    /** The asking price when the offer was made. */
    @Column(precision = 18, scale = 2)
    private BigDecimal listPrice;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal offerAmount;

    /** The price both sides accepted (set at the reservation); the payment schedule must add up to it. */
    @Column(precision = 18, scale = 2)
    private BigDecimal agreedPrice;

    private LocalDate offerDate;
    private LocalDate offerValidUntil;

    @Column(precision = 18, scale = 2)
    private BigDecimal reservationDeposit;
    private LocalDate reservationExpiry;

    private LocalDate agreementDate;
    private LocalDate expectedCompletion;
    private LocalDate completedDate;

    /** The agency's commission as a percentage of the price, unless a fixed amount is set. */
    @Column(precision = 7, scale = 2)
    private BigDecimal commissionPercent;

    @Column(precision = 18, scale = 2)
    private BigDecimal commissionFixed;

    /** The seller as a customer: the commission is invoiced to them. */
    private Long sellerCustomerId;

    /** The invoice of the agency's commission to the seller. */
    private Long feeInvoiceId;

    @Column(length = 2000)
    private String notes;

    @Column(length = 300)
    private String cancelReason;

    private String createdByName;
}
