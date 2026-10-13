package com.posapp.hotel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Everything the server sends back to the Hotel screens. */
public final class HotelDtos {

    private HotelDtos() {
    }

    public record RoomTypeDto(Long id, String name, String description, BigDecimal baseRate, int baseOccupancy, int maxOccupancy,
                              BigDecimal extraPersonRate, String amenities, boolean active, long roomCount) {
    }

    public record RoomDto(Long id, String number, String floor, Long roomTypeId, String roomTypeName, BigDecimal rate,
                          BigDecimal rateOverride, String housekeepingStatus, String housekeeper, String housekeepingNote,
                          boolean outOfOrder, boolean active, String notes,
                          boolean occupied, Long occupantBookingId, String occupantGuest, LocalDate occupantCheckOut) {
    }

    public record GuestDto(Long id, String fullName, String phone, String email, String nationality, String idType,
                           String idNumber, String address, String notes, long stays) {
    }

    public record BookingDto(Long id, String bookingNumber, Long guestId, String guestName, String guestPhone,
                             Long roomTypeId, String roomTypeName, Long roomId, String roomNumber,
                             LocalDate checkInDate, LocalDate checkOutDate, long nights, Instant actualCheckIn, Instant actualCheckOut,
                             int adults, int children, BigDecimal nightlyRate, BigDecimal extraPersonRate, BigDecimal nightlyTotal,
                             String status, String source, String notes, String cancelReason,
                             BigDecimal chargesTotal, BigDecimal paidTotal, BigDecimal balance) {
    }

    public record FolioChargeDto(Long id, String chargeType, String description, LocalDate nightDate, BigDecimal net,
                                 BigDecimal tax, BigDecimal amount, String sourceType, Long sourceId, boolean voided,
                                 String voidReason, Instant createdAt) {
    }

    public record FolioPaymentDto(Long id, String kind, String method, BigDecimal amount, String reference, String note,
                                  Instant createdAt) {
    }

    public record FolioDto(BookingDto booking, List<FolioChargeDto> charges, List<FolioPaymentDto> payments,
                           BigDecimal chargesTotal, BigDecimal taxTotal, BigDecimal deposits, BigDecimal paymentsTotal,
                           BigDecimal refunds, BigDecimal balance) {
    }

    /** What a cashier needs to pick a guest for "Charge to room". */
    public record InHouseDto(Long bookingId, String bookingNumber, String roomNumber, String guestName) {
    }

    public record DayAvailability(LocalDate date, int total, int booked, int available) {
    }

    public record TypeAvailability(Long roomTypeId, String roomTypeName, int totalRooms, List<DayAvailability> days) {
    }

    public record AvailabilityDto(LocalDate from, LocalDate to, List<TypeAvailability> types) {
    }

    public record GridBooking(Long bookingId, String bookingNumber, String guestName, LocalDate checkInDate,
                              LocalDate checkOutDate, String status) {
    }

    public record GridRoom(Long roomId, String roomNumber, String roomTypeName, boolean outOfOrder, List<GridBooking> bookings) {
    }

    public record RoomGridDto(LocalDate from, LocalDate to, List<GridRoom> rooms) {
    }

    public record MaintenanceDto(Long id, Long roomId, String roomNumber, String title, String description, String priority,
                                 String status, boolean blocksRoom, String reportedBy, Instant createdAt, Instant resolvedAt,
                                 String resolutionNote) {
    }

    public record HotelSettingsDto(boolean enabled, String postingMode, BigDecimal taxPercent, String checkInTime,
                                   String checkOutTime, String auditTime, LocalDate auditLastRun, String auditLastSummary) {
    }

    public record DashboardDto(int totalRooms, int occupied, int availableToday, int outOfOrder, int arrivalsToday,
                               int departuresToday, int dirtyRooms, BigDecimal occupancyPercent) {
    }

    public record RevenueByType(String roomTypeName, long roomNights, BigDecimal revenue) {
    }

    public record OccupancyReportDto(LocalDate from, LocalDate to, int roomCount, long roomNightsAvailable, long roomNightsSold,
                                     BigDecimal occupancyPercent, BigDecimal roomRevenue, BigDecimal adr, BigDecimal revPar,
                                     List<RevenueByType> byType) {
    }

    public record AuditResult(int staysChecked, int nightsCharged, String message) {
    }
}
