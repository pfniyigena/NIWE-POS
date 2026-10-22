package com.posapp.law;

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

/**
 * One movement of client money, kept per matter (a client's balance is the sum of its matters). Posted rows are never changed or
 * deleted: a wrong deposit is taken back with a reversal row. A payout or fee transfer starts as PENDING and is posted when a
 * second person approves it.
 */
@Getter
@Setter
@Entity
@Table(indexes = {@Index(columnList = "matter_id"), @Index(columnList = "client_customer_id"), @Index(columnList = "status")})
public class LawTrustTransaction extends BaseEntity {

    @Column(unique = true, length = 30)
    private String number;

    @Column(name = "matter_id", nullable = false)
    private Long matterId;

    @Column(nullable = false, length = 80)
    private String matterNumber;

    @Column(name = "client_customer_id", nullable = false)
    private Long clientCustomerId;

    @Column(nullable = false)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrustType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrustStatus status = TrustStatus.POSTED;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** The day the money moved (or is to move). */
    @Column(nullable = false)
    private LocalDate txDate;

    /** Bank transfer, cheque, cash, mobile money... */
    @Column(length = 40)
    private String method;

    /** Cheque number, bank reference, receipt number. */
    @Column(length = 100)
    private String reference;

    /** Who paid in, or who is being paid. */
    private String partyName;

    @Column(length = 500)
    private String purpose;

    private Long requestedByUserId;
    private String requestedByName;

    private Long decidedByUserId;
    private String decidedByName;
    private Instant decidedAt;

    @Column(length = 500)
    private String decisionNote;

    /** For a fee transfer: the invoice it pays. */
    private Long invoiceId;
    private String invoiceNumber;

    /** For a fee transfer: the payment recorded on the invoice. */
    private Long paymentId;

    /** For a reversal: the deposit it takes back. */
    private Long reversalOfId;

    /** For a deposit: it has been taken back by a reversal. */
    @Column(nullable = false)
    private boolean reversed = false;

    private Instant postedAt;

    private Long branchId;
}
