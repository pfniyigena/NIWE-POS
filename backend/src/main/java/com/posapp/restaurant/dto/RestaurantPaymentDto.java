package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantPayment;

import java.math.BigDecimal;

public record RestaurantPaymentDto(Long id, String method, BigDecimal amount) {
    public static RestaurantPaymentDto from(RestaurantPayment p) {
        return new RestaurantPaymentDto(p.getId(), p.getMethod(), p.getAmount());
    }
}
