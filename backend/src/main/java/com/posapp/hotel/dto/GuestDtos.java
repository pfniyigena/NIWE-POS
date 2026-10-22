package com.posapp.hotel.dto;

import com.posapp.hotel.dto.HotelDtos.GuestDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the guest register shows: a guest's history and spend, ID documents, duplicates, and the police / immigration register. */
public final class GuestDtos {

    private GuestDtos() {
    }

    /** One stay in a guest's history. {@code total} is what was charged including tax (voided lines left out). */
    public record StayDto(Long bookingId, String bookingNumber, LocalDate checkInDate, LocalDate checkOutDate, long nights, String status,
                          String roomNumber, String roomTypeName, String source, BigDecimal total) {
    }

    public record GuestStatsDto(long stays, long nights, BigDecimal lifetimeSpend, BigDecimal averageSpend, BigDecimal averageNightly,
                                LocalDate firstStay, LocalDate lastStay, long upcoming, long inHouse, long cancelled, long noShows,
                                String favouriteRoomType, String favouriteSource) {
    }

    public record GuestDocumentDto(Long id, Long guestId, String docType, String name, String docNumber, LocalDate expiryDate, String expiryState,
                                   String fileName, String contentType, long sizeBytes, String uploadedBy, Instant createdAt) {
    }

    public record GuestProfileDto(GuestDto guest, GuestStatsDto stats, List<StayDto> stays, List<GuestDocumentDto> documents) {
    }

    public record DuplicateGuestDto(Long id, String fullName, String phone, String email, String idNumber, long stays, BigDecimal lifetimeSpend, boolean vip, boolean blacklisted) {
    }

    /** Guests that look like one person: what they share ("same phone", "same ID number"...) and the candidates, most stays first. */
    public record DuplicateClusterDto(List<String> matchedOn, List<DuplicateGuestDto> guests) {
    }

    public record MergeResultDto(GuestDto guest, long bookingsMoved, long documentsMoved, long groupsMoved, long lostFoundMoved) {
    }

    /** One line of the police / immigration register: a guest present in the period. */
    public record RegisterRowDto(Long bookingId, String bookingNumber, Long guestId, String fullName, String nationality, String idType, String idNumber,
                                 LocalDate dateOfBirth, String address, String profession, String phone, LocalDate arrival, LocalDate departure,
                                 String roomNumber, int adults, int children, String status) {
    }
}
