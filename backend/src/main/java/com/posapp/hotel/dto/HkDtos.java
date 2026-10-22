package com.posapp.hotel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the housekeeping screens show. */
public final class HkDtos {

    private HkDtos() {
    }

    public record StaffDto(Long id, String name, boolean active, long tasks) {
    }

    public record ChecklistItemDto(Long id, String label, int sortOrder, boolean active) {
    }

    public record CheckResultDto(String label, boolean passed) {
    }

    public record HkTaskDto(Long id, Long roomId, String roomNumber, String floor, String roomTypeName, String roomStatus, String kind, String status,
                            Long staffId, String staffName, boolean dnd, boolean occupied, String guestName, LocalDate departs, boolean arrivalToday,
                            String notes, Instant startedAt, Instant finishedAt, Long minutes, String inspectionResult, String inspectionNote,
                            int attempts, List<CheckResultDto> checks) {
    }

    public record HkSummaryDto(int total, int open, int inProgress, int done, int skipped, int departures, int stayovers, int inspected, int failed) {
    }

    public record StaffLoadDto(Long staffId, String staffName, int tasks, int done, int credits) {
    }

    public record HkBoardDto(LocalDate date, List<HkTaskDto> tasks, HkSummaryDto summary, List<StaffLoadDto> load) {
    }

    public record ProductivityDto(Long staffId, String staffName, int tasks, int departures, int stayovers, Long avgDepartureMinutes,
                                  Long avgStayoverMinutes, int inspections, int failedInspections) {
    }

    public record LostFoundDto(Long id, String itemNo, String description, LocalDate foundOn, Long roomId, String roomNumber, String location, String foundBy,
                               String status, Long guestId, String guestName, String guestPhone, String claimedBy, LocalDate claimedOn, String notes) {
    }

    public record MinibarItemDto(Long id, String name, BigDecimal price, boolean taxable, boolean active) {
    }

    public record MinibarPostedDto(Long bookingId, String bookingNumber, String roomNumber, String guestName, int lines, BigDecimal total) {
    }

    public record LinenLogDto(Long id, LocalDate logDate, String item, int sent, int returned, int damaged, String notes) {
    }

    public record LinenBalanceDto(String item, int sent, int returned, int damaged, int atLaundry) {
    }
}
