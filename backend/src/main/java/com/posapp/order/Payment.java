package com.posapp.order;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SaleOrder order;

    /**
     * The paying method's code (e.g. "CASH", "CARD") — a plain string rather than a foreign key
     * into PaymentMethodEntity, so a method's display name can change later without rewriting
     * historical payments, and a deactivated method still reads back correctly on old receipts.
     * Validated against PaymentMethodService.validateActiveCodeOrThrow at checkout time.
     */
    private String method;

    private BigDecimal amount;

    /** Gift card payments: which card (and the card code with all but the last four characters hidden, for receipts). */
    private Long giftCardId;
    private String reference;
    /** Loyalty payments: how many points were spent (the amount is their money value at the time). */
    private Integer points;
}
