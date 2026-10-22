package com.posapp.restaurant.dto;

import java.time.Instant;

public record RestaurantWaitlistDto(
        Long id,
        String guestName,
        String guestPhone,
        int partySize,
        String notes,
        /** WAITING, NOTIFIED, SEATED or LEFT. */
        String status,
        int quotedMinutes,
        Instant addedAt,
        Instant notifiedAt,
        Instant closedAt,
        Long orderId,
        /** Place in the line (1 = next) while still waiting, else 0. */
        int position,
        int waitedMinutes,
        /** Name of a free table that would fit the party right now, if there is one. */
        String tableReady
) {
}
