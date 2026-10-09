package com.posapp.order.dto;

public class SetCustomerRequest {
    /** Null clears back to "Walk-in Customer". */
    private Long customerId;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
}
