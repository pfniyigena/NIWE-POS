package com.posapp.restaurant.dto;

import com.posapp.restaurant.Reservation;

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
        String confirmationCode
) {
    public static ReservationDto from(Reservation r) {
        var table = r.getTable();
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
                r.getConfirmationCode()
        );
    }
}
