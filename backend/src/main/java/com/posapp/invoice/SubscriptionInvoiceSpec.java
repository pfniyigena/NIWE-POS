package com.posapp.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a subscription invoice contains: the recurring line (quantity x unit price, with the subscription's own discount and tax) and any
 * extra lines (setup fee, credit carried forward, plan-change adjustment) which carry the same tax but no discount. Extra amounts may be negative.
 * A null {@code unitPrice} means there is no recurring line (an adjustment invoice).
 */
public record SubscriptionInvoiceSpec(Long customerId, String customerName, Long subscriptionId, String planName,
                                      BigDecimal unitPrice, int quantity,
                                      String taxCode, BigDecimal taxRatePercent, Boolean taxInclusive, BigDecimal discountPercent,
                                      LocalDate periodStart, LocalDate periodEnd, List<Extra> extras) {

    public record Extra(String description, BigDecimal amount) {
    }
}
