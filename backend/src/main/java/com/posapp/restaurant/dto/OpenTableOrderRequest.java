package com.posapp.restaurant.dto;

public class OpenTableOrderRequest {
    private int guestCount = 1;
    private Long serverId;
    private String notes;

    public int getGuestCount() { return guestCount; }
    public void setGuestCount(int guestCount) { this.guestCount = guestCount; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
