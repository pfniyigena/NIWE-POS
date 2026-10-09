package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

/** Fourier: "To use the reserved table, you need to enter reservation number." Body for ReservationController.seatByCode. */
public class SeatByCodeRequest {

    @NotBlank(message = "Reservation number is required")
    private String code;

    private Long serverId;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
}
