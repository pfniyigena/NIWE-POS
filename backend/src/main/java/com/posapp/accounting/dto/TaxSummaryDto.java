package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Admin &gt; Accounting &gt; Reports &gt; Tax Report / Tax Declaration — both screens read this
 * same general summary (Fourier's own scoping choice: a general tax-collected-vs-paid summary by
 * rate/period, not tied to any specific tax authority's official filing format). {@code
 * collectedByRate} is computed straight from completed POS sale lines' own snapshotted {@code
 * OrderLine.taxCode}/{@code taxRatePercent} (see {@code AccountingReportService.taxSummary}) —
 * the same per-line tax this app already tracks for the POS Reports package, just grouped by
 * rate here instead of by product/category. {@code taxPayableNetMovement} is the {@code
 * TAX_PAYABLE} account's own net credit movement in the ledger for the period — what's left owed
 * (or, if negative, over-collected/refunded) once returns are netted in — this app doesn't model
 * input/purchase tax (no supplier bill in this app carries a tax amount), so there's no separate
 * "tax paid" figure to subtract; {@code taxPayableNetMovement} already is the net "still owed"
 * position after any tax remitted via a manual journal entry against TAX_PAYABLE.
 */
public record TaxSummaryDto(
        Instant from,
        Instant to,
        List<TaxRateAmountDto> collectedByRate,
        BigDecimal totalCollected,
        BigDecimal taxPayableNetMovement,
        /** Every TAX_PAYABLE journal-entry line that rolled into taxPayableNetMovement above (Fourier: "Add all details") — see ReportDetailLineDto's own javadoc. */
        List<ReportDetailLineDto> taxPayableDetails
) {
}
