package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One tax rate's own collected-tax total for the Tax Report/Declaration — taxCode is the snapshot
 * OrderLine.taxCode carries (see TaxRate's own javadoc on why it's a stable code, not a live
 * reference). {@code details} is every sale order line that contributed to this rate's total
 * (Fourier: "Add all details") — see ReportDetailLineDto's own javadoc.
 */
public record TaxRateAmountDto(String taxCode, BigDecimal amount, List<ReportDetailLineDto> details) {
}
