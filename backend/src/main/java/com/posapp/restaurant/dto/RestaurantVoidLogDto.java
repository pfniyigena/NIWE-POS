package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantVoidLog;

import java.math.BigDecimal;
import java.time.Instant;

public record RestaurantVoidLogDto(Long id, String type, Long orderId, String orderLabel, String description, int quantity, BigDecimal amount,
                                   String reason, String byUser, String approvedBy, Instant createdAt) {
    public static RestaurantVoidLogDto from(RestaurantVoidLog l) {
        return new RestaurantVoidLogDto(l.getId(), l.getEntryType(), l.getOrderId(), l.getOrderLabel(), l.getDescription(), l.getQuantity(), l.getAmount(),
                l.getReason(), l.getByUser(), l.getApprovedBy(), l.getCreatedAt());
    }
}
