package com.posapp.accounting;

import com.posapp.accounting.dto.AccountAmountDto;
import com.posapp.accounting.dto.AssetsReportDto;
import com.posapp.accounting.dto.BalanceSheetDto;
import com.posapp.accounting.dto.CashFlowDto;
import com.posapp.accounting.dto.CashFlowLineDto;
import com.posapp.accounting.dto.CostCenterActivityDto;
import com.posapp.accounting.dto.FinancialTransactionDto;
import com.posapp.accounting.dto.GeneralLedgerAccountDto;
import com.posapp.accounting.dto.GroupedAmountDto;
import com.posapp.accounting.dto.JournalEntryDto;
import com.posapp.accounting.dto.LedgerLineDto;
import com.posapp.accounting.dto.ProfitAndLossDto;
import com.posapp.accounting.dto.ReportDetailLineDto;
import com.posapp.accounting.dto.TaxRateAmountDto;
import com.posapp.accounting.dto.TaxSummaryDto;
import com.posapp.accounting.dto.TrialBalanceRowDto;
import com.posapp.common.ApiException;
import com.posapp.reports.ReportExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Admin > Accounting > Reports — every report {@link AccountingReportService} builds (the
 * original four standard ledger reports, plus the later Accounting/Journals/Expenses/Incomes
 * report batch — see that class's own per-method javadoc for each one), exposed the same
 * ADMIN-only way {@code com.posapp.reports.ReportController} exposes the rest of this app's
 * reports. Mirrors that controller's own {@code from}/{@code to} -> {@link Instant} conversion
 * exactly ({@link #start}/{@link #end}, defaulting to "the last 30 days" so hitting an endpoint
 * directly still returns something sensible), kept as its own small helper pair here rather than
 * shared, for the same "not worth a cross-package dependency for one literal" reasoning {@code
 * ReturnsService.DEFAULT_EFRIS_TAX_CODE}'s javadoc already gives. Two menu items need no endpoint
 * of their own at all: "Chart of Accounts Directory" is exactly {@code GET
 * /api/accounting/accounts}, and "Trial Balance"/"Trial Nets"/"Trial Balance & Nets" are all the
 * same {@link #trialBalance} data, shown with different columns by the frontend.
 * <p>
 * Every report endpoint below also accepts {@code format} ({@code json} by default, or {@code
 * xlsx}/{@code pdf} for download — Fourier: "Export to Excel and Pdf") and, for the handful of
 * endpoints one frontend title doesn't uniquely name (Trial Balance's three variants, Profit &amp;
 * Loss/Income Statement, Tax Report/Declaration, the sixteen Expenses/Incomes reports), an
 * optional {@code reportTitle} the frontend passes along for the exported file's own title —
 * reusing the exact same {@link ReportExportService} the original POS Reports package already
 * built (title + column headers + string rows in, an .xlsx or .pdf out) rather than a second
 * exporter. A row that summarizes more than one underlying transaction (almost every report here
 * except Financial Transactions/Journals Report/Cash Flow, which are already transaction-level)
 * carries its own {@code details} — see {@link ReportDetailLineDto}'s own javadoc — which the
 * export flattens into indented sub-rows right after the summary row they belong to, via {@link
 * #withDetails}, so the downloaded file shows the exact same drill-down the on-screen table does.
 */
@RestController
@RequestMapping("/api/accounting/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AccountingReportController {

    private final AccountingReportService reportService;
    private final ReportExportService exportService;

    public AccountingReportController(AccountingReportService reportService, ReportExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    /** One account's own dated, running-balance ledger. Not part of the 24-report Reports menu (reached instead from an account's own Chart of Accounts row) — no export here. */
    @GetMapping("/ledger/{accountId}")
    public List<LedgerLineDto> ledger(@PathVariable Long accountId,
                                       @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to) {
        return reportService.ledger(accountId, start(from), end(to));
    }

    /**
     * Every account's own ledger, back to back — the Reports menu's "General Ledger" (Fourier:
     * "General ledger ... add them"), distinct from {@link #ledger} above (one account, reached
     * from Chart of Accounts, never exported).
     */
    @GetMapping("/general-ledger")
    public ResponseEntity<?> generalLedger(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(defaultValue = "json") String format) {
        List<GeneralLedgerAccountDto> accounts = reportService.generalLedger(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Account", "Date", "Entry #", "Memo / description", "Debit", "Credit", "Balance");
            List<List<String>> body = new ArrayList<>();
            for (GeneralLedgerAccountDto a : accounts) {
                String label = a.accountCode() + " — " + a.accountName();
                body.add(List.of(label, "", "", "Opening balance", "", "", money(a.openingBalance())));
                for (LedgerLineDto l : a.lines()) {
                    String description = l.description() != null && !l.description().isBlank() ? l.description() : l.memo();
                    body.add(List.of("", String.valueOf(l.entryDate()), orDash(l.entryNumber()), orDash(description),
                            money(l.debit()), money(l.credit()), money(l.runningBalance())));
                }
                body.add(List.of(label, "", "", "Closing balance", "", "", money(a.closingBalance())));
            }
            return export("General Ledger", headers, body, format);
        }
        return ResponseEntity.ok(accounts);
    }

    /** Trial Balance, Trial Nets and Trial Balance & Nets all read this exact same endpoint — they differ only in which columns the frontend's TrialBalanceComponent shows, not in the underlying data or the export (which always includes every column). */
    @GetMapping("/trial-balance")
    public ResponseEntity<?> trialBalance(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(defaultValue = "json") String format,
                                           @RequestParam(defaultValue = "Trial Balance") String reportTitle) {
        List<TrialBalanceRowDto> rows = reportService.trialBalance(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Code", "Account", "Type", "Debit", "Credit", "Balance");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            for (TrialBalanceRowDto r : rows) {
                body.add(List.of(r.accountCode(), r.accountName(), r.accountType().toString(), money(r.totalDebit()), money(r.totalCredit()), money(r.balance())));
                details.add(r.details());
            }
            return export(reportTitle, headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Backs both the Profit & Loss and Income Statement menu items (the frontend routes both to this same component/endpoint). */
    @GetMapping("/profit-and-loss")
    public ResponseEntity<?> profitAndLoss(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "json") String format,
                                            @RequestParam(defaultValue = "Profit and Loss") String reportTitle) {
        ProfitAndLossDto r = reportService.profitAndLoss(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Section", "Code", "Account", "Amount");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            for (AccountAmountDto a : r.revenue()) {
                body.add(List.of("Revenue", a.accountCode(), a.accountName(), money(a.amount())));
                details.add(a.details());
            }
            body.add(List.of("", "", "Total revenue", money(r.totalRevenue())));
            details.add(List.of());
            for (AccountAmountDto a : r.expenses()) {
                body.add(List.of("Expense", a.accountCode(), a.accountName(), money(a.amount())));
                details.add(a.details());
            }
            body.add(List.of("", "", "Total expenses", money(r.totalExpenses())));
            details.add(List.of());
            body.add(List.of("", "", "Net income", money(r.netIncome())));
            details.add(List.of());
            return export(reportTitle, headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(r);
    }

    /** A snapshot as of one date — defaults to today, mirroring {@code ReportService.stockValuation}'s own "as of now" default for the same kind of point-in-time report. */
    @GetMapping("/balance-sheet")
    public ResponseEntity<?> balanceSheet(@RequestParam(required = false) LocalDate asOf,
                                           @RequestParam(defaultValue = "json") String format) {
        BalanceSheetDto r = reportService.balanceSheet(end(asOf));
        if (isDownload(format)) {
            List<String> headers = List.of("Section", "Code", "Account", "Amount");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            for (AccountAmountDto a : r.assets()) {
                body.add(List.of("Asset", a.accountCode(), a.accountName(), money(a.amount())));
                details.add(a.details());
            }
            body.add(List.of("", "", "Total assets", money(r.totalAssets())));
            details.add(List.of());
            for (AccountAmountDto a : r.liabilities()) {
                body.add(List.of("Liability", a.accountCode(), a.accountName(), money(a.amount())));
                details.add(a.details());
            }
            body.add(List.of("", "", "Total liabilities", money(r.totalLiabilities())));
            details.add(List.of());
            for (AccountAmountDto a : r.equity()) {
                body.add(List.of("Equity", a.accountCode(), a.accountName(), money(a.amount())));
                details.add(a.details());
            }
            body.add(List.of("", "", "Current earnings (not yet closed)", money(r.currentEarnings())));
            details.add(List.of());
            body.add(List.of("", "", "Total equity", money(r.totalEquity().add(r.currentEarnings()))));
            details.add(List.of());
            return export("Balance Sheet", headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(r);
    }

    // ---- Journals Reports ----

    /** Every journal entry line across every account in range, flattened — each row already IS one transaction, so unlike most exports here this one needs no extra detail sub-rows. */
    @GetMapping("/financial-transactions")
    public ResponseEntity<?> financialTransactions(@RequestParam(required = false) LocalDate from,
                                                     @RequestParam(required = false) LocalDate to,
                                                     @RequestParam(defaultValue = "json") String format) {
        List<FinancialTransactionDto> rows = reportService.financialTransactions(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Entry #", "Account code", "Account", "Memo", "Description", "Debit", "Credit");
            List<List<String>> body = new ArrayList<>();
            for (FinancialTransactionDto r : rows) {
                body.add(List.of(String.valueOf(r.date()), orDash(r.entryNumber()), orDash(r.accountCode()), orDash(r.accountName()),
                        orDash(r.memo()), orDash(r.description()), money(r.debit()), money(r.credit())));
            }
            return export("Financial Transactions", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Every journal entry in range — each entry's own lines become that entry's {@code details} on export/on-screen, since JournalEntryDto doesn't carry a separate details field (its {@code lines} already is one). */
    @GetMapping("/journals")
    public ResponseEntity<?> journals(@RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(defaultValue = "json") String format) {
        List<JournalEntryDto> entries = reportService.journals(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Entry #", "Date", "Source", "Memo", "Total debit", "Total credit");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            for (JournalEntryDto e : entries) {
                body.add(List.of(orDash(e.entryNumber()), String.valueOf(e.entryDate()), orDash(e.sourceType()), orDash(e.memo()),
                        money(e.totalDebit()), money(e.totalCredit())));
                details.add(journalEntryDetails(e));
            }
            return export("Journals Report", headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(entries);
    }

    /** One entry's own lines, turned into the generic ReportDetailLineDto shape for export/on-screen drill-down — JournalEntryDto already carries them as {@code lines}, so this just re-shapes rather than recomputing anything. */
    private List<ReportDetailLineDto> journalEntryDetails(JournalEntryDto entry) {
        List<ReportDetailLineDto> details = new ArrayList<>();
        for (JournalEntryDto.LineDto line : entry.lines()) {
            String reference = orDash(line.accountCode()) + " — " + orDash(line.accountName());
            details.add(new ReportDetailLineDto(entry.entryDate(), reference, line.description(), line.debit(), line.credit(),
                    line.debit().subtract(line.credit())));
        }
        return details;
    }

    @GetMapping("/cost-centers")
    public ResponseEntity<?> costCenterActivity(@RequestParam(required = false) LocalDate from,
                                                  @RequestParam(required = false) LocalDate to,
                                                  @RequestParam(defaultValue = "json") String format) {
        List<CostCenterActivityDto> rows = reportService.costCenterActivity(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Code", "Cost center", "Debit", "Credit", "Net");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            for (CostCenterActivityDto r : rows) {
                body.add(List.of(r.costCenterCode(), r.costCenterName(), money(r.totalDebit()), money(r.totalCredit()), money(r.net())));
                details.add(r.details());
            }
            return export("Cost Centers", headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Accounting Reports ----

    /** Each cash movement here is already one transaction (CashFlowLineDto), so — like Financial Transactions — the export needs no extra detail sub-rows, just the Operating/Financing sections flattened with the summary figures appended. */
    @GetMapping("/cash-flow")
    public ResponseEntity<?> cashFlow(@RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(defaultValue = "json") String format) {
        CashFlowDto r = reportService.cashFlow(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Section", "Date", "Memo", "Amount");
            List<List<String>> body = new ArrayList<>();
            for (CashFlowLineDto l : r.operating()) {
                body.add(List.of("Operating", String.valueOf(l.date()), orDash(l.memo()), money(l.amount())));
            }
            for (CashFlowLineDto l : r.financing()) {
                body.add(List.of("Financing", String.valueOf(l.date()), orDash(l.memo()), money(l.amount())));
            }
            body.add(List.of("Summary", "Opening cash", "", money(r.openingCash())));
            body.add(List.of("Summary", "Net operating", "", money(r.netOperating())));
            body.add(List.of("Summary", "Net investing", "", money(r.netInvesting())));
            body.add(List.of("Summary", "Net financing", "", money(r.netFinancing())));
            body.add(List.of("Summary", "Net change in cash", "", money(r.netChangeInCash())));
            body.add(List.of("Summary", "Closing cash", "", money(r.closingCash())));
            return export("Cash Flow Report", headers, body, format);
        }
        return ResponseEntity.ok(r);
    }

    /** Backs both the Tax Report and Tax Declaration menu items — see TaxSummaryDto's own javadoc. */
    @GetMapping("/tax-summary")
    public ResponseEntity<?> taxSummary(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @RequestParam(defaultValue = "json") String format,
                                         @RequestParam(defaultValue = "Tax Report") String reportTitle) {
        TaxSummaryDto r = reportService.taxSummary(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Tax rate", "Collected");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            for (TaxRateAmountDto rate : r.collectedByRate()) {
                body.add(List.of(rate.taxCode(), money(rate.amount())));
                details.add(rate.details());
            }
            body.add(List.of("Total collected", money(r.totalCollected())));
            details.add(List.of());
            body.add(List.of("Tax payable — net movement this period", money(r.taxPayableNetMovement())));
            details.add(r.taxPayableDetails());
            return export(reportTitle, headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(r);
    }

    @GetMapping("/assets")
    public ResponseEntity<?> assets(@RequestParam(required = false) LocalDate asOf,
                                     @RequestParam(defaultValue = "json") String format) {
        AssetsReportDto r = reportService.assets(end(asOf));
        if (isDownload(format)) {
            List<String> headers = List.of("Code", "Account", "Amount");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            for (AccountAmountDto a : r.assets()) {
                body.add(List.of(a.accountCode(), a.accountName(), money(a.amount())));
                details.add(a.details());
            }
            body.add(List.of("", "Total assets", money(r.totalAssets())));
            details.add(List.of());
            return export("Assets", headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(r);
    }

    // ---- Expenses / Incomes segmented + periodic reports ----

    private static final Set<String> VALID_GROUP_BY = Set.of("category", "vendor", "staff", "client", "daily", "weekly", "monthly", "yearly");

    /** Backs all eight "Expenses ..." segmented/periodic report menu items — see AccountingReportService.expensesGrouped. */
    @GetMapping("/expenses")
    public ResponseEntity<?> expensesGrouped(@RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to,
                                              @RequestParam String groupBy,
                                              @RequestParam(defaultValue = "json") String format,
                                              @RequestParam(defaultValue = "Expenses") String reportTitle) {
        validateGroupBy(groupBy);
        List<GroupedAmountDto> rows = reportService.expensesGrouped(from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(30),
                to != null ? to : LocalDate.now(ZoneOffset.UTC), groupBy);
        return groupedResponse(rows, format, reportTitle);
    }

    /** Backs all eight "Incomes ..." segmented/periodic report menu items — see AccountingReportService.incomesGrouped. */
    @GetMapping("/incomes")
    public ResponseEntity<?> incomesGrouped(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam String groupBy,
                                             @RequestParam(defaultValue = "json") String format,
                                             @RequestParam(defaultValue = "Incomes") String reportTitle) {
        validateGroupBy(groupBy);
        List<GroupedAmountDto> rows = reportService.incomesGrouped(from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(30),
                to != null ? to : LocalDate.now(ZoneOffset.UTC), groupBy);
        return groupedResponse(rows, format, reportTitle);
    }

    private ResponseEntity<?> groupedResponse(List<GroupedAmountDto> rows, String format, String reportTitle) {
        if (isDownload(format)) {
            List<String> headers = List.of("Label", "Count", "Amount");
            List<List<String>> body = new ArrayList<>();
            List<List<ReportDetailLineDto>> details = new ArrayList<>();
            long totalCount = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (GroupedAmountDto r : rows) {
                body.add(List.of(r.label(), String.valueOf(r.count()), money(r.amount())));
                details.add(r.details());
                totalCount += r.count();
                totalAmount = totalAmount.add(r.amount());
            }
            body.add(List.of("Total", String.valueOf(totalCount), money(totalAmount)));
            details.add(List.of());
            return export(reportTitle, headers, withDetails(body, details), format);
        }
        return ResponseEntity.ok(rows);
    }

    private void validateGroupBy(String groupBy) {
        if (!VALID_GROUP_BY.contains(groupBy)) {
            throw ApiException.badRequest("groupBy must be one of " + VALID_GROUP_BY);
        }
    }

    // ---- export helpers ----

    private static final List<String> DETAIL_HEADERS = List.of("Detail date", "Detail reference", "Detail description", "Detail debit", "Detail credit");

    /**
     * Appends each summary row's own {@code details} (if any) as extra sub-rows right after it,
     * sharing five "Detail ..." columns added once to every row — blank on the summary row
     * itself, populated only on the detail rows beneath it (Fourier: "Add all details"). Headers
     * are appended by the caller passing them through {@link #export} as-is plus {@link
     * #DETAIL_HEADERS}; this method only reshapes the body.
     */
    private List<List<String>> withDetails(List<List<String>> summaryRows, List<List<ReportDetailLineDto>> detailsPerRow) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < summaryRows.size(); i++) {
            List<String> summary = summaryRows.get(i);
            List<String> row = new ArrayList<>(summary);
            for (int k = 0; k < DETAIL_HEADERS.size(); k++) {
                row.add("");
            }
            out.add(row);
            List<ReportDetailLineDto> details = i < detailsPerRow.size() ? detailsPerRow.get(i) : null;
            if (details == null) continue;
            for (ReportDetailLineDto d : details) {
                List<String> detailRow = new ArrayList<>();
                for (int c = 0; c < summary.size(); c++) {
                    detailRow.add("");
                }
                detailRow.add(d.date() == null ? "" : String.valueOf(d.date()));
                detailRow.add(orDash(d.reference()));
                detailRow.add(orDash(d.description()));
                detailRow.add(d.debit() == null ? "" : money(d.debit()));
                detailRow.add(d.credit() == null ? "" : money(d.credit()));
                out.add(detailRow);
            }
        }
        return out;
    }

    private boolean isDownload(String format) {
        return "xlsx".equalsIgnoreCase(format) || "pdf".equalsIgnoreCase(format);
    }

    /** Every exportable endpoint's headers list already ends with {@link #DETAIL_HEADERS} by the time it reaches here whenever it called {@link #withDetails} — this just hands both off to the same generic exporter the POS Reports package built. */
    private ResponseEntity<byte[]> export(String title, List<String> headers, List<List<String>> rows, String format) {
        List<String> finalHeaders = rows.stream().anyMatch(r -> r.size() > headers.size())
                ? concat(headers, DETAIL_HEADERS)
                : headers;
        byte[] content = "xlsx".equalsIgnoreCase(format)
                ? exportService.toXlsx(title, finalHeaders, rows)
                : exportService.toPdf(title, finalHeaders, rows);
        String filename = title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ("xlsx".equalsIgnoreCase(format) ? ".xlsx" : ".pdf");
        MediaType mediaType = "xlsx".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }

    private List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    // ---- helpers (see class javadoc) ----

    private Instant start(LocalDate from) {
        LocalDate effective = from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        return effective.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant end(LocalDate to) {
        LocalDate effective = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        return effective.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    }
}
