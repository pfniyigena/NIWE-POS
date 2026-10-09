package com.posapp.warehouse.dto;

import jakarta.validation.constraints.NotNull;

public record StockTransferRequest(
        @NotNull(message = "Product is required") Long productId,
        @NotNull(message = "Source location is required") Long fromLocationId,
        @NotNull(message = "Destination location is required") Long toLocationId,
        /** Not validated as positive here — StockTransferService rejects zero/negative with a clearer message than a bare constraint violation would give. */
        int quantity,
        String note
) {
}
