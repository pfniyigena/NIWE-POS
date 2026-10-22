package com.posapp.booking.dto;

import com.posapp.booking.BlockTarget;
import com.posapp.booking.PaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class BookingRequests {

    private BookingRequests() {
    }

    public record SettingsRequest(LocalTime dayStart, LocalTime dayEnd, String workDays, Integer slotStepMinutes, Integer minNoticeMinutes,
                                  Integer maxAdvanceDays, Integer holdMinutes, Integer cancelNoticeHours, Integer maxPublicPerPhonePerDay,
                                  Boolean publicEnabled, Boolean emailConfirmations, String publicTitle, String publicIntro, String paymentInstructions) {
    }

    public record ServiceRequest(String name, String description, Integer durationMinutes, Integer bufferMinutes, BigDecimal price, PaymentMode paymentMode,
                                 Integer depositPercent, String resourceKind, String color, Boolean active, Boolean publicVisible, List<Long> staffIds) {
    }

    public record ResourceRequest(String name, String kind, Integer capacity, String notes, Boolean active, Long branchId) {
    }

    public record BlockRequest(BlockTarget targetType, Long targetId, LocalDateTime startAt, LocalDateTime endAt, String reason) {
    }

    /** A staff booking. {@code skipPayment} confirms at once even if the service asks for a deposit (paid at the desk). */
    public record BookRequest(Long serviceId, LocalDateTime start, Long staffUserId, Long resourceId, Long customerId, String guestName, String guestPhone,
                              String guestEmail, String notes, Boolean skipPayment) {
    }

    public record RescheduleRequest(LocalDateTime start, Long staffUserId, Long resourceId) {
    }

    public record ReasonRequest(String reason) {
    }

    public record NotesRequest(String notes) {
    }

    public record PublicBookRequest(Long serviceId, LocalDate date, String time, Long staffUserId, String name, String phone, String email, String notes,
                                    String website) {
    }

    public record PublicPhoneRequest(String phone) {
    }
}
