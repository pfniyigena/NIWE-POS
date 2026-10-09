package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One movement of the CASH account within a CashFlowDto section — amount is signed (positive = cash in, negative = cash out). */
public record CashFlowLineDto(Instant date, String memo, BigDecimal amount) {
}
