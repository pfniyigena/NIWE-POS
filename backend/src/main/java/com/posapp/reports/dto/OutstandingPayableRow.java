package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * Task: "Add Supplier payment" — the accounts-payable mirror of {@link OutstandingCreditRow}:
 * snapshot, no date range, one row per supplier this store currently owes anything to, built from
 * every purchase order ever placed with them (see {@code SupplierPaymentService.outstandingBalance}).
 */
public record OutstandingPayableRow(
        Long supplierId,
        String supplierName,
        String phone,
        BigDecimal outstandingBalance
) {
}
