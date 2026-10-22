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
                          boolean occupied, Long occupantBookingId, String occupantGuest, LocalDate occupantCheckOut, boolean dnd) {
    }

    public record GuestDto(Long id, String fullName, String phone, String email, String nationality, String idType,
                           String idNumber, String address, String notes, long stays,
                           boolean vip, boolean blacklisted, String blacklistReason, String preferences, LocalDate dateOfBirth, String profession) {
    }

    public record BookingDto(Long id, String bookingNumber, Long guestId, String guestName, String guestPhone,
                             Long roomTypeId, String roomTypeName, Long roomId, String roomNumber,
                             LocalDate checkInDate, LocalDate checkOutDate, long nights, Instant actualCheckIn, Instant actualCheckOut,
                             int adults, int children, BigDecimal nightlyRate, BigDecimal extraPersonRate, BigDecimal nightlyTotal,
                             String status, String source, String notes, String cancelReason,
                             BigDecimal chargesTotal, BigDecimal paidTotal, BigDecimal balance,
                             Long ratePlanId, String ratePlanName, String rateMode,
                             Long groupId, String groupCode, String groupName,
                             boolean guestVip, boolean guestBlacklisted, String guestPreferences,
                             String confirmationCode, boolean online, BigDecimal depositDue, Instant holdExpiresAt) {
    }

    public record FolioChargeDto(Long id, String chargeType, String description, LocalDate nightDate, BigDecimal net,
                                 BigDecimal tax, BigDecimal amount, String sourceType, Long sourceId, boolean voided,
                                 String voidReason, Instant createdAt, int billNo) {
    }

    public record FolioPaymentDto(Long id, String kind, String method, BigDecimal amount, String reference, String note,
                                  Instant createdAt, int billNo) {
    }

    public record FolioDto(BookingDto booking, List<FolioChargeDto> charges, List<FolioPaymentDto> payments,
                           BigDecimal chargesTotal, BigDecimal taxTotal, BigDecimal deposits, BigDecimal paymentsTotal,
                           BigDecimal refunds, BigDecimal balance, List<BillDto> bills) {
    }

    /** One numbered bill of a stay: what is on it, what was taken for it, and whether it goes to a company. */
    public record BillDto(int billNo, String label, Long companyId, String companyName, boolean roomNights, BigDecimal charges,
                          BigDecimal paid, BigDecimal balance, Long companyBillId, String companyBillNumber, String companyBillStatus,
                          boolean toGroup) {
    }

    /** A fee the policy would charge: kind is CANCELLATION, NO_SHOW, EARLY_DEPARTURE or LATE_CHECKOUT. */
    public record FeeQuoteDto(String kind, String description, int nights, BigDecimal net, BigDecimal tax, BigDecimal gross) {
    }

    public record CancelPreviewDto(FeeQuoteDto fee, BigDecimal depositHeld, BigDecimal feeFromDeposit, BigDecimal depositLeft,
                                   BigDecimal stillOwed) {
    }

    public record CheckoutPreviewDto(List<FeeQuoteDto> fees, BigDecimal guestDue, BigDecimal companyDue, BigDecimal toPay) {
    }

    public record CompanyReceiptDto(Long id, String method, BigDecimal amount, String reference, LocalDate receivedOn) {
    }

    public record CompanyBillDto(Long id, String billNumber, Long companyId, String companyName, Long bookingId, String bookingNumber,
                                 String guestName, int billNo, String description, BigDecimal amount, BigDecimal paid,
                                 BigDecimal outstanding, String status, LocalDate issuedOn, LocalDate dueDate, boolean overdue,
                                 List<CompanyReceiptDto> receipts) {
    }

    /** A customer that can be billed on account: what the bill picker offers. */
    public record CompanyDto(Long id, String name, String taxId, BigDecimal creditLimit, BigDecimal owing, Integer termDays) {
    }

    public record CompanySummaryDto(Long companyId, String companyName, BigDecimal billed, BigDecimal paid, BigDecimal outstanding,
                                    BigDecimal overdue, int openBills) {
    }

    /** What a cashier needs to pick a guest for "Charge to room". */
    public record InHouseDto(Long bookingId, String bookingNumber, String roomNumber, String guestName) {
    }

    public record DayAvailability(LocalDate date, int total, int booked, int held, int available) {
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
                                 String resolutionNote, LocalDate blockFrom, LocalDate blockUntil, List<String> conflicts) {
    }

    public record HotelSettingsDto(boolean enabled, String postingMode, BigDecimal taxPercent, String checkInTime,
                                   String checkOutTime, String auditTime, LocalDate auditLastRun, String auditLastSummary,
                                   int cancelFreeDays, int cancelFeeNights, int noShowFeeNights, int earlyDepartureFeeNights,
                                   String lateCheckoutFrom, BigDecimal lateCheckoutPercent, boolean taxOnFees) {
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

    // ---- rates (HO-2) ----

    public record RatePlanDto(Long id, String name, String description, BigDecimal adjustPercent, BigDecimal adjustAmount, int minNights,
                              boolean active, long bookings) {
    }

    public record RateRuleDto(Long id, String name, Long roomTypeId, String roomTypeName, LocalDate startDate, LocalDate endDate,
                              String daysOfWeek, String mode, BigDecimal value, int minNights, int priority, boolean active) {
    }

    /** One night of a quote: the room price, any extra-guest charge, tax, and which rule set the price. */
    public record QuoteNightDto(LocalDate date, BigDecimal rate, BigDecimal extra, BigDecimal net, BigDecimal tax, BigDecimal gross, String ruleName) {
    }

    public record QuoteDto(Long roomTypeId, String roomTypeName, LocalDate from, LocalDate to, int nights, Long ratePlanId, String ratePlanName,
                           List<QuoteNightDto> items, BigDecimal net, BigDecimal tax, BigDecimal gross, BigDecimal averageRate) {
    }

    public record RateCalendarDay(LocalDate date, BigDecimal rate, String ruleName, int available) {
    }

    public record RateCalendarDto(Long roomTypeId, String roomTypeName, BigDecimal baseRate, Long ratePlanId, String ratePlanName,
                                  List<RateCalendarDay> days) {
    }
}
