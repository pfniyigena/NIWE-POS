package com.posapp.order.dto;

import com.posapp.order.Payment;

import java.math.BigDecimal;

public record PaymentDto(Long id, String method, BigDecimal amount,
                         /** Gift card payments: the card code with all but the last four characters hidden. */
                         String reference,
                         /** Loyalty payments: the points spent. */
                         Integer points) {
    public static PaymentDto from(Payment payment) {
        return new PaymentDto(payment.getId(), payment.getMethod(), payment.getAmount(), payment.getReference(), payment.getPoints());
    }
}
