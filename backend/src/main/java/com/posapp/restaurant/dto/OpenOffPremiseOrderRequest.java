package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

/** Opens a TAKEAWAY or DELIVERY order (Fourier: "Delivery & takeaway") — no table, so a customer name stands in for "which table" wherever the UI needs to identify the order. */
public class OpenOffPremiseOrderRequest {
    @NotBlank(message = "Customer name is required")
    private String customerName;
    private String customerPhone;
    /** Required for delivery orders; ignored for takeaway — enforced in RestaurantOrderService.openDeliveryOrder rather than here, since the same DTO serves both endpoints. */
    private String customerAddress;
    private int guestCount = 1;
    private Long serverId;
    private String notes;

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public int getGuestCount() { return guestCount; }
    public void setGuestCount(int guestCount) { this.guestCount = guestCount; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
