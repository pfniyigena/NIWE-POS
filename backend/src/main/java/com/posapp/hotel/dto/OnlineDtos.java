package com.posapp.hotel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Online booking (HO-8): the manager's settings, and what the public booking page shows and accepts. */
public final class OnlineDtos {

    private OnlineDtos() {
    }

    public record OnlineSettingsDto(boolean enabled, String intro, BigDecimal depositPercent, int holdHours, int maxNights, int maxDaysAhead,
                                    int maxPerPhonePerDay, String paymentInstructions, boolean mailConfirmation, boolean mailPrearrival,
                                    int mailPrearrivalDays, boolean mailThanks, String thanksMessage, String reviewUrl, boolean mailReady) {
    }

    public record OnlineSettingsRequest(Boolean enabled, String intro, BigDecimal depositPercent, Integer holdHours, Integer maxNights,
                                        Integer maxDaysAhead, Integer maxPerPhonePerDay, String paymentInstructions, Boolean mailConfirmation,
                                        Boolean mailPrearrival, Integer mailPrearrivalDays, Boolean mailThanks, String thanksMessage, String reviewUrl) {
    }

    /** A room type as the manager sees it for the website: whether it is offered, and its iCal feed address (the secret part only once made). */
    public record OnlineRoomTypeDto(Long id, String name, String description, boolean active, boolean onlineVisible, String icalToken, String icalPath) {
    }

    public record OnlineRoomTypeRequest(Boolean onlineVisible, String description) {
    }

    public record PublicInfoDto(String hotelName, String address, String phone, String email, String intro, String checkInTime, String checkOutTime,
                                BigDecimal taxPercent, String currency, BigDecimal depositPercent, int holdHours, int maxNights, int maxDaysAhead,
                                String paymentInstructions) {
    }

    public record PublicOfferDto(Long roomTypeId, String name, String description, String amenities, int maxOccupancy, int roomsLeft, int nights,
                                 BigDecimal net, BigDecimal tax, BigDecimal gross, BigDecimal averageRate, BigDecimal depositDue, List<PublicNightDto> items) {
    }

    public record PublicNightDto(LocalDate date, BigDecimal gross) {
    }

    public record PublicAvailabilityDto(LocalDate from, LocalDate to, int nights, int adults, int children, List<PublicOfferDto> offers) {
    }

    public record PublicBookRequest(Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate, Integer adults, Integer children,
                                    String name, String phone, String email, String notes, String website) {
    }

    public record PublicPhoneRequest(String phone) {
    }

    public record PublicBookingDto(String code, String bookingNumber, String status, String roomTypeName, LocalDate checkInDate, LocalDate checkOutDate,
                                   long nights, int adults, int children, String guestName, BigDecimal total, BigDecimal depositDue, BigDecimal depositPaid,
                                   Instant holdExpiresAt, String paymentInstructions, boolean canCancel, String cancelNote, String checkInTime,
                                   String checkOutTime) {
    }
}
