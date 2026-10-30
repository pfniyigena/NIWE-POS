package com.posapp.subscription.dto;

import com.posapp.subscription.SubscriptionEvent;
import com.posapp.subscription.SubscriptionEventKind;

import java.time.Instant;

public record SubscriptionEventDto(Long id, SubscriptionEventKind kind, String detail, String by, Instant at) {
    public static SubscriptionEventDto from(SubscriptionEvent e) {
        return new SubscriptionEventDto(e.getId(), e.getKind(), e.getDetail(), e.getCreatedByName(), e.getCreatedAt());
    }
}
