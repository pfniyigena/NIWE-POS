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

/**
 * The sale of one unit to one buyer. One invoice for the agreed price is issued when the agreement is signed; the buyer then pays it at once or in
 * instalments (an Installments plan on that invoice).
 */
@Getter
@Setter
@Entity
@Table(name = "re_unit_sale", indexes = {@Index(columnList = "unit_id"), @Index(columnList = "project_id"), @Index(columnList = "stage")})
public class ReUnitSale extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    private Long leadId;
    private Long buyerCustomerId;

    @Column(nullable = false, length = 200)
    private String buyerName;

    @Column(length = 40)
    private String buyerPhone;

    private String buyerEmail;

    private Long agentUserId;
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitSaleStage stage = UnitSaleStage.RESERVED;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal listPrice;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal agreedPrice;

    private LocalDate reservedOn;
    private LocalDate reservationExpiry;
    private LocalDate agreementDate;
    private LocalDate completedDate;

    private Long invoiceId;
    private String invoiceNumber;

    @Column(length = 2000)
    private String notes;

    @Column(length = 300)
    private String cancelReason;

    private String createdByName;
}
