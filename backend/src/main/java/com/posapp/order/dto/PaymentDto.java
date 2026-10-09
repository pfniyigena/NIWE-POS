package com.posapp.order.dto;

import com.posapp.order.Payment;

import java.math.BigDecimal;

public record PaymentDto(Long id, String method, BigDecimal amount) {
    public static PaymentDto from(Payment payment) {
        return new PaymentDto(payment.getId(), payment.getMethod(), payment.getAmount());
    }
}
