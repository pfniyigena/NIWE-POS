package com.posapp.restaurant.dto;

import java.math.BigDecimal;

/** What the public booking page needs to know before it asks anything: opening hours, how far ahead, and the deposit. */
public record ReservationInfoDto(String restaurantName, String openTime, String closeTime, int slotMinutes, int turnMinutes,
                                 int bookingDaysAhead, BigDecimal depositPerGuest, String today, String timeZone) {
}
