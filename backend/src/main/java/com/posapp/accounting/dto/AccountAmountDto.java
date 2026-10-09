package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One account's own line on the Profit &amp; Loss, Balance Sheet, or Assets report — a name and
 * its period/as-of amount, plus every journal-entry line that rolled into it (Fourier: "Add all
 * details") — see ReportDetailLineDto's own javadoc.
 */
public record AccountAmountDto(Long accountId, String accountCode, String accountName, BigDecimal amount, List<ReportDetailLineDto> details) {
}
