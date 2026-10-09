package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One underlying transaction backing a summary report row, added across every Accounting Report
 * so a summary figure (e.g. "Rent expense: 450.00") can be expanded to show every line that made
 * it up (Fourier: "The report show the summary, Add all details"). One generic shape for every
 * report family rather than one detail-row type per report: {@code debit}/{@code credit} hold the
 * raw ledger-line amounts exactly as posted for journal-line-backed detail (Trial Balance, Profit
 * &amp; Loss, Balance Sheet, Assets, Cost Centers, the Tax Payable net-movement figure), or a
 * directional stand-in for Expense/Income-backed detail (an expense's own amount goes in {@code
 * debit} — the same side a real posting of that expense would hit — an income's in {@code
 * credit}) and Tax-collected detail (the line's own collected tax in {@code credit}, since
 * collecting tax is itself a credit to Tax Payable). {@code amount} is always populated too
 * (signed net, debit minus credit) so a caller that only wants one number doesn't need to know
 * which family it's reading.
 */
public record ReportDetailLineDto(
        Instant date,
        String reference,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal amount
) {
}
