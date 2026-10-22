package com.posapp.restaurant.dto;

public record ReservationReminderDto(String text, boolean emailed, String note) {
}
