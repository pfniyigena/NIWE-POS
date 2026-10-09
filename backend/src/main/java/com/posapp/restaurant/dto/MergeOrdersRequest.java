package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public class MergeOrdersRequest {
    @NotNull(message = "Target order is required")
    private Long targetOrderId;

    public Long getTargetOrderId() { return targetOrderId; }
    public void setTargetOrderId(Long targetOrderId) { this.targetOrderId = targetOrderId; }
}
