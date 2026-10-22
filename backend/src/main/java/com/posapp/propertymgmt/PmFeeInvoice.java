package com.posapp.propertymgmt;

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
import java.time.Instant;
import java.time.LocalDate;

/** The invoice the agency gives an owner for its management fee and the VAT on it, for one agreed owner statement. */
@Getter
@Setter
@Entity
@Table(name = "pm_fee_invoice", indexes = {@Index(columnList = "owner_id"), @Index(columnList = "issue_date"), @Index(columnList = "statement_id")})
public class PmFeeInvoice extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String number;

    /** The statement it was issued for (it may have been deleted since; the code below is kept). */
    private Long statementId;

    @Column(length = 20)
    private String statementCode;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PmFeeInvoiceStatus status = PmFeeInvoiceStatus.ISSUED;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal netAmount;

    @Column(precision = 7, scale = 2)
    private BigDecimal vatPercent;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 200)
    private String ownerName;

    @Column(length = 80)
    private String ownerTaxNumber;

    @Column(length = 500)
    private String ownerAddress;

    @Column(length = 255)
    private String sellerName;

    @Column(length = 80)
    private String sellerTaxId;

    @Column(length = 500)
    private String sellerAddress;

    @Column(length = 80)
    private String sellerPhone;

    @Column(length = 255)
    private String sellerEmail;

    private String issuedByName;

    @Column(length = 500)
    private String voidReason;

    private Instant voidedAt;
}
