package com.posapp.hotel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the group screens show. */
public final class GroupDtos {

    private GroupDtos() {
    }

    public record GroupSummaryDto(Long id, String groupCode, String name, String status, String billing, LocalDate arrivalDate,
                                  LocalDate departureDate, String companyName, String contactName, int roomsBlocked, int roomsBooked,
                                  int checkedIn, BigDecimal owed, BigDecimal held) {
    }

    /** A block with how many of its rooms the group has booked; {@code holding} says whether the rooms are still being kept back. */
    public record GroupBlockDto(Long id, Long roomTypeId, String roomTypeName, int rooms, BigDecimal rate, LocalDate startDate,
                                LocalDate endDate, LocalDate releaseDate, boolean released, boolean holding, int booked, int unbooked) {
    }

    /** One room of the rooming list. {@code unnamed} is true while the room is still booked under the group's contact. */
    public record RoomingDto(Long bookingId, String bookingNumber, Long guestId, String guestName, String guestPhone, boolean unnamed,
                             Long roomTypeId, String roomTypeName, Long roomId, String roomNumber, LocalDate checkInDate, LocalDate checkOutDate,
                             int adults, int children, String status, BigDecimal charges, BigDecimal paid, String billTo) {
    }

    public record GroupEntryDto(Long id, String kind, Long bookingId, String bookingNumber, Integer billNo, String description, BigDecimal amount,
                                String method, String reference, LocalDate entryDate) {
    }

    public record GroupTotalsDto(BigDecimal billed, BigDecimal deposits, BigDecimal applied, BigDecimal paid, BigDecimal refunded,
                                 BigDecimal owed, BigDecimal held) {
    }

    public record GroupDetailDto(Long id, String groupCode, String name, String contactName, String contactPhone, String contactEmail,
                                 Long companyId, String companyName, LocalDate arrivalDate, LocalDate departureDate, String status,
                                 String billing, String notes, int roomsBlocked, int roomsBooked, int checkedIn,
                                 List<GroupBlockDto> blocks, List<RoomingDto> rooming, List<GroupEntryDto> entries, GroupTotalsDto totals) {
    }

    public record NamesResultDto(int assigned, int leftover, GroupDetailDto group) {
    }
}
