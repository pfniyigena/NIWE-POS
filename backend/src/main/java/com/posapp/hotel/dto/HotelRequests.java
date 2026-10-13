package com.posapp.hotel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything the Hotel screens send to the server. */
public final class HotelRequests {

    private HotelRequests() {
    }

    public record RoomTypeRequest(@NotBlank(message = "Name is required") String name, String description,
                                  @NotNull(message = "Nightly rate is required") BigDecimal baseRate,
                                  Integer baseOccupancy, Integer maxOccupancy, BigDecimal extraPersonRate,
                                  String amenities, Boolean active) {
    }

    public record RoomRequest(@NotBlank(message = "Room number is required") String number, String floor,
                              @NotNull(message = "Room type is required") Long roomTypeId, BigDecimal rateOverride,
                              String notes, Boolean active) {
    }

    public record GuestRequest(@NotBlank(message = "Guest name is required") String fullName, String phone, String email,
                               String nationality, String idType, String idNumber, String address, String notes) {
    }

    /** A new booking. Either pick an existing guest (guestId) or give the new guest's details (guest). */
    public record BookingRequest(Long guestId, @Valid GuestRequest guest,
                                 @NotNull(message = "Room type is required") Long roomTypeId, Long roomId,
                                 @NotNull(message = "Arrival date is required") LocalDate checkInDate,
                                 @NotNull(message = "Departure date is required") LocalDate checkOutDate,
                                 Integer adults, Integer children, BigDecimal nightlyRate, String source, String notes,
                                 BigDecimal depositAmount, String depositMethod) {
    }

    /** Changes to a booking that has not been checked out: dates, room, guests, rate, notes. */
    public record BookingUpdateRequest(Long roomTypeId, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                       Integer adults, Integer children, BigDecimal nightlyRate, String source, String notes) {
    }

    /** depositAction: REFUND (give back any deposit) or FORFEIT (keep it). refundMethod: how a refund is paid out. */
    public record CancelRequest(String reason, String depositAction, String refundMethod) {
    }

    public record CheckInRequest(Long roomId, Integer adults, Integer children, Boolean allowNotReady) {
    }

    /** A guest who arrives without a booking: guest, room and dates in one step, checked in straight away. */
    public record WalkInRequest(Long guestId, @Valid GuestRequest guest, @NotNull(message = "Room is required") Long roomId,
                                @NotNull(message = "Departure date is required") LocalDate checkOutDate,
                                Integer adults, Integer children, BigDecimal nightlyRate, String notes,
                                BigDecimal depositAmount, String depositMethod) {
    }

    public record MoveRoomRequest(@NotNull(message = "Room is required") Long roomId, String reason) {
    }

    public record ExtendStayRequest(@NotNull(message = "New departure date is required") LocalDate checkOutDate) {
    }

    public record PaymentLine(@NotBlank(message = "Payment method is required") String method,
                              @NotNull(message = "Amount is required") BigDecimal amount, String reference) {
    }

    /** refundMethod: how any money owed back to the guest is paid (defaults to CASH). */
    public record CheckOutRequest(@Valid List<PaymentLine> payments, String refundMethod) {
    }

    /** A manual charge on the bill (laundry, minibar, late check-out...). A negative amount is a discount. */
    public record ChargeRequest(@NotBlank(message = "Description is required") String description,
                                @NotNull(message = "Amount is required") BigDecimal amount, Boolean taxable) {
    }

    public record FolioPaymentRequest(@NotBlank(message = "Kind is required") String kind,
                                      @NotBlank(message = "Payment method is required") String method,
                                      @NotNull(message = "Amount is required") BigDecimal amount, String reference, String note) {
    }

    public record VoidRequest(String reason) {
    }

    public record HousekeepingRequest(@NotBlank(message = "Status is required") String status, String housekeeper, String note) {
    }

    public record MaintenanceRequestForm(@NotNull(message = "Room is required") Long roomId,
                                         @NotBlank(message = "Title is required") String title, String description,
                                         String priority, Boolean blocksRoom, String reportedBy) {
    }

    public record MaintenanceUpdateRequest(String status, String priority, Boolean blocksRoom, String resolutionNote) {
    }

    public record HotelSettingsRequest(Boolean enabled, String postingMode, BigDecimal taxPercent, String checkInTime,
                                       String checkOutTime, String auditTime) {
    }
}
