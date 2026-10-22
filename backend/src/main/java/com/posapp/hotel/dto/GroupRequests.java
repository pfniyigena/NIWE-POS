package com.posapp.hotel.dto;

import com.posapp.hotel.dto.HotelRequests.GuestRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GroupRequests {

    private GroupRequests() {
    }

    /** billing: INDIVIDUAL, MASTER_ROOMS or MASTER_ALL (default INDIVIDUAL). */
    public record GroupRequest(@NotBlank(message = "Group name is required") String name, String contactName, String contactPhone,
                               String contactEmail, Long companyId,
                               @NotNull(message = "Arrival date is required") LocalDate arrivalDate,
                               @NotNull(message = "Departure date is required") LocalDate departureDate, String billing, String notes) {
    }

    /** Dates default to the whole group stay. */
    public record BlockRequest(@NotNull(message = "Room type is required") Long roomTypeId,
                               @NotNull(message = "Number of rooms is required") Integer rooms, BigDecimal rate,
                               LocalDate startDate, LocalDate endDate, LocalDate releaseDate) {
    }

    /** Books {@code count} rooms of a type for the group (or one per name when names are given). Rate: given, else the block's, else the hotel's. */
    public record AddRoomsRequest(@NotNull(message = "Room type is required") Long roomTypeId, Integer count, List<String> names,
                                  Integer adults, Integer children, LocalDate checkInDate, LocalDate checkOutDate, BigDecimal nightlyRate,
                                  Long ratePlanId) {
    }

    /** Names (or a guest) and details for one room of the rooming list. */
    public record RoomingUpdateRequest(Long guestId, @Valid GuestRequest guest, Integer adults, Integer children, Long roomId) {
    }

    public record NamesRequest(List<String> names) {
    }

    public record GroupMoneyRequest(String method, @NotNull(message = "Amount is required") BigDecimal amount, String reference, String note) {
    }

    public record GroupCancelRequest(String reason, Boolean waiveFees) {
    }
}
