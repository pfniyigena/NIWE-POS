package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A saved three-way reconciliation: the bank statement, the client ledgers and the general ledger, on one day. */
@Getter
@Setter
@Entity
public class LawTrustReconciliation extends BaseEntity {

    @Column(nullable = false)
    private LocalDate asOfDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal statementBalance = BigDecimal.ZERO;

    /** Deposits the bank has not shown yet. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal depositsInTransit = BigDecimal.ZERO;

    /** Payouts (cheques) that have not cleared yet. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingPayments = BigDecimal.ZERO;

    /** Statement + deposits in transit - outstanding payments. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustedBank = BigDecimal.ZERO;

    /** The sum of every client's balance in the client ledgers. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal ledgerTotal = BigDecimal.ZERO;

    /** The trust bank account in the general ledger. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal glBank = BigDecimal.ZERO;

    /** The client-money liability in the general ledger. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal glLiability = BigDecimal.ZERO;

    /** Adjusted bank minus the client ledgers; zero when everything agrees. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal difference = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean balanced = false;

    @Column(length = 1000)
    private String note;

    private Long preparedByUserId;
    private String preparedByName;
}
