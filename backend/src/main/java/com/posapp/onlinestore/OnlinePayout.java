package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * v1.71.0: one payout from a payment provider (MTN/Airtel mobile money or the card provider) to the shop's bank: what the customers paid (gross), the fee the
 * provider kept and what reached the bank (net). Each one is posted to the books (clearing account to Cash and Bank and Payment Provider Fees).
 */
@Getter
@Setter
@Entity
public class OnlinePayout extends BaseEntity {
    /** MOBILE_MONEY or CARD. */
    private String channel;
    private BigDecimal gross;
    private BigDecimal fee;
    private BigDecimal net;
    private String reference;
    private String note;
    private LocalDate paidOn;
    private String createdBy;
}
