package com.posapp.realestate.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** What the printing screens read. */
public final class RealEstatePrintDtos {

    private RealEstatePrintDtos() {
    }

    /** One payment received, with the number of its receipt. {@code what} says what it paid (the invoice or the milestone). */
    public record PaymentRow(Long id, Instant paidAt, BigDecimal amount, String method, String receiptNumber, String what) {
    }
}
