package com.posapp.restaurant.dto;

import java.math.BigDecimal;

/**
 * Fourier: "Public be able to make reservation" — the confirmation handed back to an anonymous
 * booking. Deliberately narrower than {@link ReservationDto}: no {@code restaurantOrderId} (only
 * ever set once staff seat the party) and nothing about the guest's history. {@code tableName} IS
 * included since the guest chooses their own table from the availability list. {@code
 * confirmationCode} is the "reservation number" — this is the one and only place a guest ever
 * sees it, so the booking page must show it prominently. {@code depositAmount} is what the guest
 * is asked to pay at the restaurant (0 = none).
 */
public record PublicReservationDto(
        Long id,
        String guestName,
        int partySize,
        String reservedFor,
        String tableName,
        String status,
        String notes,
        String confirmationCode,
        int durationMinutes,
        BigDecimal depositAmount,
        String depositStatus
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
                dto.confirmationCode(),
                dto.durationMinutes(),
                dto.depositAmount(),
                dto.depositStatus()
        );
    }
}
