package com.posapp.restaurant.dto;

/**
 * Fourier: "Public reservations should show available table for that day." A deliberately slim
 * projection of {@link DiningTableDto} for the public availability picker — no {@code status}/
 * {@code active}/{@code openOrderId}: those describe live, right-now front-of-house state
 * ("currently seated with a walk-in") that has nothing to do with a table's availability on some
 * future date, and would only confuse an anonymous visitor booking ahead.
 */
public record PublicTableDto(Long id, String name, String section, int capacity) {
    public static PublicTableDto from(DiningTableDto t) {
        return new PublicTableDto(t.id(), t.name(), t.section(), t.capacity());
    }
}
