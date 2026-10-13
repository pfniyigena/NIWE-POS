package com.posapp.installment;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Money received through the plan. The {@link #principalPart} is recorded as an ordinary payment on the invoice (that is
 * {@link #customerPaymentId}); the {@link #chargesPart} (interest and late fees) is income of its own, posted to Accounting directly.
 */
@Getter
@Setter
@Entity
public class InstallmentPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InstallmentPlan plan;

    @Column(nullable = false)
    private Instant paidAt = Instant.now();

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal principalPart = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal chargesPart = BigDecimal.ZERO;

    private String method;
    private String receiptNumber;
    private Long customerPaymentId;
    private String note;
    private String recordedByName;
}
