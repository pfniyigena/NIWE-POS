package com.posapp.restaurant.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CashEventRequest {
    /** PAID_IN, PAID_OUT or TIPS_PAID. */
    private String kind;
    private BigDecimal amount;
    private String reason;
}
