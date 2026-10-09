package com.posapp.restaurant.dto;

/**
 * Fourier: "Public be able to make reservation" — the confirmation handed back to an anonymous
 * booking. Deliberately narrower than {@link ReservationDto}: no {@code restaurantOrderId} (only
 * ever set once staff seat the party). {@code tableName} IS included (unlike an earlier revision
 * of this class) since Fourier's follow-up has the guest choosing their own table from the
 * availability list — they already know which one it is. {@code confirmationCode} is the
 * "reservation number" (Fourier: "To use the reserved table, you need to enter reservation
 * number.") — this is the one and only place a guest ever sees it, so the booking page must show
 * it prominently and tell them to keep it.
 */
public record PublicReservationDto(
        Long id,
        String guestName,
        int partySize,
        String reservedFor,
        String tableName,
        String status,
        String notes,
        String confirmationCode
) {
    public static PublicReservationDto from(ReservationDto dto) {
        return new PublicReservationDto(
                dto.id(),
                dto.guestName(),
                dto.partySize(),
                dto.reservedFor() != null ? dto.reservedFor().toString() : null,
                dto.tableName(),
                dto.status(),
                dto.notes(),
                dto.confirmationCode()
        );
    }
}
