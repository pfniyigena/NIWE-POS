package com.posapp.register.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of the voids-and-returns log: a voided sale, a return, or a cart dropped before it was paid. */
public record PosExceptionDto(
        String type,
        Instant at,
        String ref,
        Long orderId,
        Long returnId,
        String customer,
        String by,
        String registerName,
        Long registerId,
        BigDecimal amount,
        String reason,
        String approvedBy,
        Long sessionId
) {
}
