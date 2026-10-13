package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotNull;

/** Hotel module: opens a room-service order for the in-house guest with this booking. */
public class OpenRoomServiceOrderRequest {
    @NotNull(message = "Choose the guest's room")
    private Long bookingId;
    private Long serverId;
    private String notes;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
