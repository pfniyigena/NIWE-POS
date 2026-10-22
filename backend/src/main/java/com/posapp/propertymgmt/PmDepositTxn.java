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

/** One movement of a lease's security deposit. A mistake is voided (the entry stays, marked, and the books are reversed), never deleted. */
@Getter
@Setter
@Entity
@Table(name = "pm_deposit_txn", indexes = {@Index(columnList = "lease_id")})
public class PmDepositTxn extends BaseEntity {

    @Column(name = "lease_id", nullable = false)
    private Long leaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PmDepositKind kind;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    /** How the money came or went (cash, bank, mobile money...). */
    @Column(length = 40)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(length = 14)
    private PmDepositReason reason;

    @Column(length = 1000)
    private String note;

    /** The rent invoice a DEDUCTION for unpaid rent settled. */
    private Long invoiceId;

    /** The receipt of the payment that settled it. */
    @Column(length = 60)
    private String receiptNumber;

    /** For a transfer: the lease on the other side, and its matching entry. */
    private Long otherLeaseId;

    private Long otherTxnId;

    @Column(nullable = false)
    private boolean voided = false;

    @Column(length = 500)
    private String voidReason;

    private Instant voidedAt;

    private String recordedByName;
}
