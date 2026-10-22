package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class ReservationRequest {
    @NotBlank(message = "Guest name is required")
    private String guestName;
    private String guestPhone;
    private int partySize = 2;
    @NotNull(message = "Reservation date/time is required")
    private Instant reservedFor;
    private Long tableId;
    private String notes;
    /** How long the party keeps the table (minutes); blank uses the restaurant's usual turn time. */
    private Integer durationMinutes;
    private String guestEmail;
    /** Deposit asked for; blank uses the per-guest amount from the settings. */
    private java.math.BigDecimal depositAmount;
    /** When set, the deposit is taken now with this payment method. */
    private String depositMethod;

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
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }
    public java.math.BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(java.math.BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public String getDepositMethod() { return depositMethod; }
    public void setDepositMethod(String depositMethod) { this.depositMethod = depositMethod; }
}
