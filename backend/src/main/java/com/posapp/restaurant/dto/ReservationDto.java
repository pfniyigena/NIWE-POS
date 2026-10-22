package com.posapp.restaurant.dto;

import com.posapp.restaurant.Reservation;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservationDto(
        Long id,
        String guestName,
        String guestPhone,
        int partySize,
        Instant reservedFor,
        Long tableId,
        String tableName,
        String status,
        String notes,
        Long restaurantOrderId,
        /** The "reservation number" — see Reservation's own javadoc. */
        String confirmationCode,
        /** How long the table is held, in minutes (the booking's own or the restaurant's usual turn time). */
        int durationMinutes,
        Instant endsAt,
        String guestEmail,
        BigDecimal depositAmount,
        /** NONE, REQUIRED, HELD, APPLIED, REFUNDED or FORFEITED. */
        String depositStatus,
        String depositMethod,
        Instant remindedAt,
        /** STAFF or ONLINE. */
        String source,
        String cancelReason,
        Instant noShowAt,
        /** How many earlier bookings under the same phone number ended as no-shows. */
        int guestNoShows,
        /** How many earlier bookings under the same phone number were seated. */
        int guestVisits
) {
    public static ReservationDto from(Reservation r) {
        return from(r, 90, 0, 0);
    }

    public static ReservationDto from(Reservation r, int defaultTurnMinutes, int guestNoShows, int guestVisits) {
        var table = r.getTable();
        int minutes = r.getDurationMinutes() != null ? r.getDurationMinutes() : defaultTurnMinutes;
        return new ReservationDto(
                r.getId(),
                r.getGuestName(),
                r.getGuestPhone(),
                r.getPartySize(),
                r.getReservedFor(),
                table != null ? table.getId() : null,
                table != null ? table.getName() : null,
                r.getStatus().name(),
                r.getNotes(),
                r.getRestaurantOrderId(),
                r.getConfirmationCode(),
                minutes,
                r.getReservedFor() == null ? null : r.getReservedFor().plusSeconds(minutes * 60L),
                r.getGuestEmail(),
                r.getDepositAmount() == null ? BigDecimal.ZERO : r.getDepositAmount(),
                r.getDepositStatus(),
                r.getDepositMethod(),
                r.getRemindedAt(),
                r.getSource(),
                r.getCancelReason(),
                r.getNoShowAt(),
                guestNoShows,
                guestVisits
        );
    }
}
