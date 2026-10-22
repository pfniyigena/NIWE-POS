package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Cash that moved outside a bill: a deposit taken, refunded or kept, or money put in or taken out of the drawer by hand
 * (petty cash, tips paid to staff). The daily cash-up reads these together with the day's payments.
 */
@Getter
@Setter
@Entity
public class RestaurantCashEvent extends BaseEntity {

    /** DEPOSIT_RECEIVED, DEPOSIT_REFUNDED, DEPOSIT_FORFEITED, PAID_IN, PAID_OUT or TIPS_PAID. */
    private String kind;
    /** Payment method code for deposits (only a cash method touches the drawer). */
    private String method;
    private BigDecimal amount = BigDecimal.ZERO;
    private Long reservationId;
    private String label;
    private String reason;
    private String byUser;
}
