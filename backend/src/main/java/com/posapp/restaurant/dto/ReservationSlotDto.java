package com.posapp.restaurant.dto;

import java.time.Instant;

/** One bookable time on a day: when it starts, how it reads on the local clock, and how many tables could take the party. */
public record ReservationSlotDto(Instant time, String label, int tablesFree) {
}
