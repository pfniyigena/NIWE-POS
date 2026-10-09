package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Admin &gt; Accounting &gt; Reports &gt; Cash Flow Report — see AccountingReportService.cashFlow's
 * own javadoc for the direct-method classification rule (Operating/Investing/Financing). {@code
 * investing} is always empty today — this app models no investing activity (asset
 * purchases/sales outside inventory) — kept as a real (if always-empty) section rather than
 * omitted, so the report's shape already matches a standard three-section cash flow statement if
 * that's ever modeled later.
 */
public record CashFlowDto(
        Instant from,
        Instant to,
        List<CashFlowLineDto> operating,
        List<CashFlowLineDto> investing,
        List<CashFlowLineDto> financing,
        BigDecimal netOperating,
        BigDecimal netInvesting,
        BigDecimal netFinancing,
        BigDecimal netChangeInCash,
        BigDecimal openingCash,
        BigDecimal closingCash
) {
}
