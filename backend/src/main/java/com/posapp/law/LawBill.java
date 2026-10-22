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

/**
 * What was billed from a matter: one draft invoice (or, when a retainer covered everything, no invoice) with what went on it.
 * Its time entries are kept so the whole bill can be taken back.
 */
@Getter
@Setter
@Entity
@Table(indexes = @Index(columnList = "matter_id"))
public class LawBill extends BaseEntity {

    @Column(name = "matter_id", nullable = false)
    private Long matterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillKind kind = BillKind.INVOICE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillStatus status = BillStatus.ACTIVE;

    private Long invoiceId;
    private String invoiceNumber;

    /** What the client is asked to pay on the invoice (after the retainer applied). */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** Fees before the retainer was taken off. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal feesAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal expensesAmount = BigDecimal.ZERO;

    /** Retainer used up by this bill. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal retainerApplied = BigDecimal.ZERO;

    /** The amount recovered for the client, when this bill is a contingency fee. */
    @Column(precision = 18, scale = 2)
    private BigDecimal recoveredAmount;

    @Column(length = 1000)
    private String summary;

    /** Comma-separated ids of the time entries on this bill. */
    @Column(length = 4000)
    private String timeEntryIds;

    private String createdByName;

    /** How many reminders the client has had about this invoice. */
    @jakarta.persistence.Column(nullable = false)
    private int reminderCount = 0;

    private java.time.Instant lastRemindedAt;
}
