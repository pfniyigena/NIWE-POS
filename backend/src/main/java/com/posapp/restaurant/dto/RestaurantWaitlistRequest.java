package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public class RestaurantWaitlistRequest {
    @NotBlank(message = "Guest name is required")
    private String guestName;
    private String guestPhone;
    private int partySize = 2;
    private String notes;
    /** Minutes promised to the guest; blank = estimate from the tables in use. */
    private Integer quotedMinutes;

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public String getGuestPhone() { return guestPhone; }
    public void setGuestPhone(String guestPhone) { this.guestPhone = guestPhone; }
    public int getPartySize() { return partySize; }
    public void setPartySize(int partySize) { this.partySize = partySize; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Integer getQuotedMinutes() { return quotedMinutes; }
    public void setQuotedMinutes(Integer quotedMinutes) { this.quotedMinutes = quotedMinutes; }
}
