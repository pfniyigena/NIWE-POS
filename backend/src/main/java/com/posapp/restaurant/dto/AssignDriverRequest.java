package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotNull;

/** Fourier: "Delivery & takeaway" — assigns (or reassigns) a staff member as the driver on a DELIVERY order's deliveries board. */
public class AssignDriverRequest {
    @NotNull(message = "Driver is required")
    private Long driverId;

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
}
