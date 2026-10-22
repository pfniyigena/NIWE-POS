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

    /** vip / blacklisted left out (null) keep what the guest already has; blacklisting needs a reason. */
    public record GuestRequest(@NotBlank(message = "Guest name is required") String fullName, String phone, String email,
                               String nationality, String idType, String idNumber, String address, String notes,
                               Boolean vip, Boolean blacklisted, String blacklistReason, String preferences, LocalDate dateOfBirth, String profession) {
        public GuestRequest(String fullName, String phone, String email, String nationality, String idType, String idNumber, String address, String notes) {
            this(fullName, phone, email, nationality, idType, idNumber, address, notes, null, null, null, null, null, null);
        }
    }

    /** A new booking. Either pick an existing guest (guestId) or give the new guest's details (guest). */
    public record BookingRequest(Long guestId, @Valid GuestRequest guest,
                                 @NotNull(message = "Room type is required") Long roomTypeId, Long roomId,
                                 @NotNull(message = "Arrival date is required") LocalDate checkInDate,
                                 @NotNull(message = "Departure date is required") LocalDate checkOutDate,
                                 Integer adults, Integer children, BigDecimal nightlyRate, String source, String notes,
                                 BigDecimal depositAmount, String depositMethod, Long ratePlanId) {
    }

    /** Changes to a booking that has not been checked out: dates, room, guests, rate, notes. */
    public record BookingUpdateRequest(Long roomTypeId, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                       Integer adults, Integer children, BigDecimal nightlyRate, String source, String notes,
                                       Long ratePlanId, Boolean autoRate) {
    }

    /** depositAction: REFUND (give back any deposit) or FORFEIT (keep it). refundMethod: how a refund is paid out. */
    public record CancelRequest(String reason, String depositAction, String refundMethod, Boolean waiveFee) {
    }

    public record CheckInRequest(Long roomId, Integer adults, Integer children, Boolean allowNotReady) {
    }

    /** A guest who arrives without a booking: guest, room and dates in one step, checked in straight away. */
    public record WalkInRequest(Long guestId, @Valid GuestRequest guest, @NotNull(message = "Room is required") Long roomId,
                                @NotNull(message = "Departure date is required") LocalDate checkOutDate,
                                Integer adults, Integer children, BigDecimal nightlyRate, String notes,
                                BigDecimal depositAmount, String depositMethod, Long ratePlanId) {
    }

    public record MoveRoomRequest(@NotNull(message = "Room is required") Long roomId, String reason) {
    }

    public record ExtendStayRequest(@NotNull(message = "New departure date is required") LocalDate checkOutDate) {
    }

    public record PaymentLine(@NotBlank(message = "Payment method is required") String method,
                              @NotNull(message = "Amount is required") BigDecimal amount, String reference) {
    }

    /** refundMethod: how any money owed back to the guest is paid (defaults to CASH). */
    public record CheckOutRequest(@Valid List<PaymentLine> payments, String refundMethod, Boolean waiveFees) {
    }

    /** A manual charge on the bill (laundry, minibar, late check-out...). A negative amount is a discount. */
    public record ChargeRequest(@NotBlank(message = "Description is required") String description,
                                @NotNull(message = "Amount is required") BigDecimal amount, Boolean taxable, Integer billNo) {
    }

    public record FolioPaymentRequest(@NotBlank(message = "Kind is required") String kind,
                                      @NotBlank(message = "Payment method is required") String method,
                                      @NotNull(message = "Amount is required") BigDecimal amount, String reference, String note, Integer billNo) {
    }

    public record VoidRequest(String reason) {
    }

    /** Create or change a numbered bill of a stay. companyId sends it to a company; clearCompany takes it back to the guest. */
    public record BillRequest(String label, Long companyId, Boolean clearCompany, Boolean toGroup) {
        public BillRequest(String label, Long companyId, Boolean clearCompany) {
            this(label, companyId, clearCompany, null);
        }
    }

    public record AssignChargeRequest(@NotNull(message = "Bill number is required") Integer billNo) {
    }

    public record MoveChargeRequest(@NotNull(message = "Target booking is required") Long targetBookingId, String reason) {
    }

    public record CompanyReceiptRequest(@NotBlank(message = "Payment method is required") String method,
                                        @NotNull(message = "Amount is required") BigDecimal amount, String reference, LocalDate receivedOn) {
    }

    public record HousekeepingRequest(@NotBlank(message = "Status is required") String status, String housekeeper, String note) {
    }

    public record MaintenanceRequestForm(@NotNull(message = "Room is required") Long roomId,
                                         @NotBlank(message = "Title is required") String title, String description,
                                         String priority, Boolean blocksRoom, String reportedBy,
                                         LocalDate blockFrom, LocalDate blockUntil) {
    }

    public record MaintenanceUpdateRequest(String status, String priority, Boolean blocksRoom, String resolutionNote,
                                           LocalDate blockFrom, LocalDate blockUntil, Boolean clearBlockDates) {
    }

    public record HotelSettingsRequest(Boolean enabled, String postingMode, BigDecimal taxPercent, String checkInTime,
                                       String checkOutTime, String auditTime,
                                       Integer cancelFreeDays, Integer cancelFeeNights, Integer noShowFeeNights,
                                       Integer earlyDepartureFeeNights, String lateCheckoutFrom, BigDecimal lateCheckoutPercent,
                                       Boolean taxOnFees) {
        /** Settings changes that do not touch the fee policy (module activation, tests). */
        public HotelSettingsRequest(Boolean enabled, String postingMode, BigDecimal taxPercent, String checkInTime, String checkOutTime, String auditTime) {
            this(enabled, postingMode, taxPercent, checkInTime, checkOutTime, auditTime, null, null, null, null, null, null, null);
        }
    }

    /** adjustPercent: +10 / -15; adjustAmount: added to each night after the percentage. */
    public record RatePlanRequest(@NotBlank(message = "Name is required") String name, String description, BigDecimal adjustPercent,
                                  BigDecimal adjustAmount, Integer minNights, Boolean active) {
    }

    /** mode: FIXED (value is the nightly rate), PERCENT (value is +/- percent) or AMOUNT (value is +/- per night). daysOfWeek: "FRI,SAT" or empty. */
    public record RateRuleRequest(@NotBlank(message = "Name is required") String name, Long roomTypeId,
                                  @NotNull(message = "Start date is required") LocalDate startDate, LocalDate endDate, String daysOfWeek,
                                  @NotBlank(message = "Mode is required") String mode, @NotNull(message = "Value is required") BigDecimal value,
                                  Integer minNights, Integer priority, Boolean active) {
    }
}
