package com.posapp.restaurant.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Fourier: "Public be able to make reservation" — the unauthenticated version of {@link
 * ReservationRequest}, posted from the public booking page (no login) via {@code
 * PublicReservationController}. {@code reservedFor} is validated {@code @Future} here
 * specifically — the staff-facing {@link ReservationRequest} has never enforced that (a staff
 * member might legitimately log a walk-in reservation after the fact), but a public visitor
 * booking a table in the past makes no sense and would otherwise silently create a dead booking
 * no one reviews.
 *
 * <p>Fourier: "Public reservations should show available table for that day. The person then
 * book table. Once Booked it can not be used by anyone." {@code tableId} is now required and
 * chosen from {@code PublicReservationController.availableTables} — the safety this reverses
 * from an earlier revision (a raw client-supplied {@code tableId} unchecked against anything) is
 * restored server-side instead: {@code ReservationService.create}'s own {@code
 * assertTableAvailable} re-validates the table is active, in service, and not already booked
 * that day regardless of what the client claims, so a stale or tampered choice is still rejected.
 */
public class PublicReservationRequest {

    @NotBlank(message = "Name is required")
    private String guestName;

    @NotBlank(message = "Phone number is required")
    private String guestPhone;

    private int partySize = 2;

    @NotNull(message = "Reservation date/time is required")
    @Future(message = "Reservation date/time must be in the future")
    private Instant reservedFor;

    @NotNull(message = "Select a table")
    private Long tableId;

    private String notes;

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public String getGuestPhone() { return guestPhone; }
    public void setGuestPhone(String guestPhone) { this.guestPhone = guestPhone; }
    public int getPartySize() { return partySize; }
    public void setPartySize(int partySize) { this.partySize = partySize; }
    public Instant getReservedFor() { return reservedFor; }
    public void setReservedFor(Instant reservedFor) { this.reservedFor = reservedFor; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    /** Converts to the internal {@link ReservationRequest} that {@code ReservationService.create} expects. */
    public ReservationRequest toReservationRequest() {
        ReservationRequest request = new ReservationRequest();
        request.setGuestName(guestName);
        request.setGuestPhone(guestPhone);
        request.setPartySize(partySize);
        request.setReservedFor(reservedFor);
        request.setNotes(notes);
        request.setTableId(tableId);
        return request;
    }
}
