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
import java.time.LocalDate;

/**
 * What the agency owes one owner for a period: the rent collected on the owner's properties, less the management fee, less the repairs the owner pays for, plus or minus manual
 * adjustments. The totals are kept on the statement and recalculated whenever its lines change.
 */
@Getter
@Setter
@Entity
@Table(name = "pm_owner_statement", indexes = {@Index(columnList = "owner_id"), @Index(columnList = "status")})
public class PmOwnerStatement extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private StatementStatus status = StatementStatus.DRAFT;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal rentCollected = BigDecimal.ZERO;

    /** The management fee, as a positive amount. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal managementFee = BigDecimal.ZERO;

    /** The VAT on the management fee, as a positive amount, taken from the owner. Zero when no VAT was charged. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    /** The VAT rate the statement was built with, kept so that a later change of the rate does not alter it. */
    @Column(precision = 7, scale = 2)
    private BigDecimal vatPercent;

    /** Repairs the owner pays for, as a positive amount. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal repairs = BigDecimal.ZERO;

    /** Manual lines, signed: positive adds to what the owner gets. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustments = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal netPayable = BigDecimal.ZERO;

    @Column(length = 2000)
    private String notes;

    private LocalDate finalizedDate;

    @Column(length = 255)
    private String finalizedByName;

    private LocalDate paidDate;

    @Column(length = 40)
    private String paidMethod;

    @Column(length = 120)
    private String paidReference;

    @Column(length = 255)
    private String createdByName;

    /** The agreement has been booked to the ledger (statements agreed before ledger posting existed have not). */
    @Column(nullable = false)
    private boolean agreementPosted;

    /** The payout has been booked to the ledger. */
    @Column(nullable = false)
    private boolean payoutPosted;

    /** When the statement was last e-mailed to the owner, and to which address. */
    private java.time.Instant emailedAt;

    @Column(length = 255)
    private String emailedTo;
}
