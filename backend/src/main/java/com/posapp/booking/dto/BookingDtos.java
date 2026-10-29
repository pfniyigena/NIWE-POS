package com.posapp.booking.dto;

import com.posapp.booking.AppointmentSource;
import com.posapp.booking.AppointmentStatus;
import com.posapp.booking.BlockTarget;
import com.posapp.booking.PaymentMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record Option(Long id, String name) {
    }

    public record SettingsDto(LocalTime dayStart, LocalTime dayEnd, String workDays, int slotStepMinutes, int minNoticeMinutes, int maxAdvanceDays,
                              int holdMinutes, int cancelNoticeHours, int maxPublicPerPhonePerDay, boolean publicEnabled, boolean emailConfirmations,
                              String publicTitle, String publicIntro, String paymentInstructions, boolean reminderEnabled, int reminderHours,
                              boolean allowGuestReschedule, int maxGuestReschedules) {
    }

    public record ServiceDto(Long id, String name, String description, int durationMinutes, int bufferMinutes, BigDecimal price, PaymentMode paymentMode,
                             int depositPercent, BigDecimal depositAmount, String resourceKind, String color, boolean active, boolean publicVisible,
                             List<Option> staff, long appointments) {
    }

    public record ResourceDto(Long id, String name, String kind, Integer capacity, String notes, boolean active, Long branchId, long appointments) {
    }

    public record BlockDto(Long id, BlockTarget targetType, Long targetId, String targetName, LocalDateTime startAt, LocalDateTime endAt, String reason,
                           String createdByName) {
    }

    public record AppointmentDto(Long id, String number, String confirmationCode, AppointmentStatus status, AppointmentSource source, Long serviceId,
                                 String serviceName, String serviceColor, LocalDateTime startAt, LocalDateTime endAt, int durationMinutes, Long staffUserId,
                                 String staffName, Long resourceId, String resourceName, Long customerId, String guestName, String guestPhone,
                                 String guestEmail, String notes, BigDecimal price, PaymentMode paymentMode, BigDecimal amountDue, BigDecimal amountPaid,
                                 Long invoiceId, String invoiceNumber, Instant holdExpiresAt, String cancelReason, Instant createdAt, String createdByName,
                                 boolean canWork, boolean canManage, Instant reminderSentAt, int rescheduleCount, GuestHistoryDto history) {
    }

    /** What the business already knows about this guest (matched by customer or phone number), not counting this appointment. */
    public record GuestHistoryDto(long completed, long noShows, long cancelled, long upcoming, LocalDateTime lastVisit) {
    }

    public record SlotDto(String time, List<Option> staff, List<Option> resources) {
    }

    /** One person's availability on the day shown: off (day off, leave) or working between start and end. */
    public record StaffDayDto(Long userId, boolean working, String start, String end, String note) {
    }

    public record DayDto(String date, List<AppointmentDto> appointments, List<BlockDto> blocks, List<Option> staff, List<Option> resources,
                         String dayStart, String dayEnd, boolean workDay, List<StaffDayDto> staffDays) {
    }

    // ---- staff schedules

    public record HoursDto(String day, String start, String end) {
    }

    public record LeaveDto(Long id, LocalDate fromDate, LocalDate toDate, String reason, String createdByName) {
    }

    /** {@code custom} false = follows the business opening hours. */
    public record ScheduleDto(Long userId, String userName, boolean custom, List<HoursDto> days, List<LeaveDto> leave) {
    }

    public record LeaveAddedDto(LeaveDto leave, long appointmentsInPeriod) {
    }


    public record SummaryDto(long today, long upcomingWeek, long awaitingPayment, long noShowsThisMonth) {
    }

    // ---- public page

    public record PublicServiceDto(Long id, String name, String description, int durationMinutes, BigDecimal price, PaymentMode paymentMode,
                                   BigDecimal amountDue, List<Option> staff) {
    }

    public record PublicInfoDto(String title, String intro, String paymentInstructions, int maxAdvanceDays, String workDays, List<PublicServiceDto> services) {
    }

    public record PublicConfirmationDto(String confirmationCode, String number, AppointmentStatus status, String serviceName, LocalDateTime startAt,
                                        LocalDateTime endAt, String staffName, BigDecimal price, BigDecimal amountDue, BigDecimal amountPaid,
                                        String invoiceNumber, Instant holdExpiresAt, String paymentInstructions, boolean canCancel, boolean canReschedule) {
    }
}
