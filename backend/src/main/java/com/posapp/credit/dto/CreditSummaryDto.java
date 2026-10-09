package com.posapp.credit.dto;

import java.math.BigDecimal;

/**
 * A customer's credit standing at a glance (Task: "Customer balance on POS screen") — open to any
 * logged-in user (see {@code CustomerCreditController}), unlike every other credit endpoint,
 * because the terminal and checkout both need it to decide whether/how much credit to extend;
 * only the underlying actions (enabling credit, setting the limit, settling a payment) are
 * ADMIN-only. {@code availableCredit} is zero (not negative, not the limit) when credit isn't
 * enabled at all, and floored at zero even if a limit was lowered below an already-outstanding
 * balance.
 */
public record CreditSummaryDto(
        Long customerId,
        boolean creditEnabled,
        BigDecimal creditLimit,
        BigDecimal outstandingBalance,
        BigDecimal availableCredit,
        Integer defaultCreditTermDays
) {
}
