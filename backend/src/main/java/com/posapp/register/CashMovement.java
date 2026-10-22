package com.posapp.register;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cash that moved in or out of a till drawer outside a sale: money paid in, money paid out, a drop into the safe, or a count made in the
 * middle of the shift (which moves nothing; it only records what was found). A manager can void one while the shift is still open.
 */
@Getter
@Setter
@Entity
public class CashMovement extends BaseEntity {

    private Long registerSessionId;
    private Long cashierSessionId;
    /** PAID_IN, PAID_OUT, DROP or COUNT. */
    private String kind;
    private String reasonCode;
    private BigDecimal amount = BigDecimal.ZERO;
    /** COUNT only: what the system expected at that moment, and the difference found. */
    private BigDecimal expectedCash;
    private BigDecimal variance;
    private String note;
    @Column(columnDefinition = "text")
    private String countDetail;
    private String byUser;
    private String approvedBy;
    private boolean voided;
    private String voidReason;
    private String voidedBy;
    private Instant voidedAt;
    private Long branchId;
}
