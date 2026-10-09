package com.posapp.dataimport;

import com.posapp.accounting.AccountingReportService;
import com.posapp.accounting.dto.AccountAmountDto;
import com.posapp.accounting.dto.BalanceSheetDto;
import com.posapp.accounting.dto.GeneralLedgerAccountDto;
import com.posapp.accounting.dto.LedgerLineDto;
import com.posapp.accounting.dto.ProfitAndLossDto;
import com.posapp.accounting.dto.TrialBalanceRowDto;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.inventory.StockAdjustmentService;
import com.posapp.inventory.SupplierPaymentService;
import com.posapp.inventory.dto.StockAdjustmentDto;
import com.posapp.inventory.dto.SupplierPaymentDto;
import com.posapp.reports.ReportExportService;
import com.posapp.reports.ReportService;
import com.posapp.reports.dto.PurchaseOrderReportRow;
import com.posapp.reports.dto.SalesOrderReportRow;
import com.posapp.reports.dto.ShiftTransactionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fourier: "no ... year-end archive/export ... for closing out a financial year." Deliberately a
 * curated snapshot of that year's business activity, not a full database dump — that's what
 * {@code com.posapp.backup.BackupService} is for; this is the one an owner hands to an accountant
 * or files away once a year closes. One {@code .zip} containing a handful of {@code .xlsx} files,
 * each built with the same {@link ReportExportService#toXlsx} every POS Report already uses, each
 * filtered to the requested calendar year.
 * <p>
 * Sales Orders and Purchase Orders reuse {@link ReportService}'s own date-ranged queries
 * directly — Customer/Supplier payment history and Stock Adjustments have no date-ranged query of
 * their own (nothing before this needed one), so those three are filtered to the year in Java
 * after fetching each service's own already-public, all-time list — an acceptable one-off cost
 * for a report that runs at most a few times a year, not worth adding three new repository
 * queries for.
 * <p>
 * Fourier: "If we don't have those accounting reports add them and include them into Year-end
 * archive — General ledger, Trial Balance, Income statement, Balance sheet." All four reuse
 * {@link AccountingReportService}'s own methods exactly as {@code AccountingReportController}'s
 * exports do (General Ledger is the one of the four that was genuinely missing — see that
 * service's own {@code generalLedger} javadoc; Trial Balance/Income Statement/Balance Sheet
 * already existed as Reports-menu items, just not folded into this archive before). Balance Sheet
 * is a point-in-time snapshot, so it's taken as of December 31 of the requested year rather than
 * over the year's date range the other three use.
 */
@Service
public class YearEndArchiveService {

    private final ReportService reportService;
    private final AccountingReportService accountingReportService;
    private final CustomerCreditService customerCreditService;
    private final SupplierPaymentService supplierPaymentService;
    private final StockAdjustmentService stockAdjustmentService;
    private final ReportExportService exportService;

    public YearEndArchiveService(ReportService reportService, AccountingReportService accountingReportService,
                                  CustomerCreditService customerCreditService, SupplierPaymentService supplierPaymentService,
                                  StockAdjustmentService stockAdjustmentService, ReportExportService exportService) {
        this.reportService = reportService;
        this.accountingReportService = accountingReportService;
        this.customerCreditService = customerCreditService;
        this.supplierPaymentService = supplierPaymentService;
        this.stockAdjustmentService = stockAdjustmentService;
        this.exportService = exportService;
    }

    @Transactional(readOnly = true)
    public byte[] exportYear(int year) {
        Instant start = Instant.from(java.time.LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC));
        Instant endExclusive = Instant.from(java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC));
        Instant endInclusive = endExclusive.minusMillis(1);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            addEntry(zip, "sales-orders.xlsx", salesOrdersXlsx(start, endExclusive));
            addEntry(zip, "pos-sales.xlsx", posSalesXlsx(start, endExclusive));
            addEntry(zip, "purchase-orders.xlsx", purchaseOrdersXlsx(start, endExclusive));
            addEntry(zip, "customer-payments.xlsx", customerPaymentsXlsx(start, endExclusive));
            addEntry(zip, "supplier-payments.xlsx", supplierPaymentsXlsx(start, endExclusive));
            addEntry(zip, "stock-adjustments.xlsx", stockAdjustmentsXlsx(start, endExclusive));
            addEntry(zip, "general-ledger.xlsx", generalLedgerXlsx(start, endInclusive));
            addEntry(zip, "trial-balance.xlsx", trialBalanceXlsx(start, endInclusive));
            addEntry(zip, "income-statement.xlsx", incomeStatementXlsx(start, endInclusive));
            addEntry(zip, "balance-sheet.xlsx", balanceSheetXlsx(endInclusive));
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build the year-end archive for " + year, e);
        }
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private byte[] salesOrdersXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Order #", "Customer", "Status", "Order date", "Expected", "Due date", "Lines", "Qty ordered", "Qty fulfilled", "Total amount", "Outstanding");
        List<List<String>> rows = new ArrayList<>();
        for (SalesOrderReportRow r : reportService.salesOrders(from, to, null)) {
            rows.add(List.of(orDash(r.orderNumber()), orDash(r.customerName()), r.status(), String.valueOf(r.orderDate()),
                    r.expectedDate() == null ? "—" : String.valueOf(r.expectedDate()),
                    r.dueDate() == null ? "—" : String.valueOf(r.dueDate()), String.valueOf(r.lineCount()),
                    String.valueOf(r.quantityOrdered()), String.valueOf(r.quantityFulfilled()), money(r.totalAmount()), money(r.amountOutstanding())));
        }
        return exportService.toXlsx("Sales Orders", headers, rows);
    }

    private byte[] posSalesXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Invoice", "Shift", "Register", "Cashier", "Customer", "Completed at", "Items", "Subtotal", "Tax", "Grand total", "Payment");
        List<List<String>> rows = new ArrayList<>();
        for (ShiftTransactionRow r : reportService.shiftTransactions(from, to, null, null)) {
            rows.add(List.of(orDash(r.invoiceNumber()), "#" + r.shiftId(), r.registerName(), r.cashierName(), r.customerName(),
                    String.valueOf(r.completedAt()), String.valueOf(r.itemCount()), money(r.subtotal()), money(r.taxTotal()),
                    money(r.grandTotal()), r.paymentMethods()));
        }
        return exportService.toXlsx("POS Sales", headers, rows);
    }

    private byte[] purchaseOrdersXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Order #", "Supplier", "Status", "Order date", "Expected", "Lines", "Qty ordered", "Qty received", "Total cost", "Paid", "Outstanding");
        List<List<String>> rows = new ArrayList<>();
        for (PurchaseOrderReportRow r : reportService.purchaseOrders(from, to, null)) {
            rows.add(List.of(orDash(r.orderNumber()), orDash(r.supplierName()), r.status(), String.valueOf(r.orderDate()),
                    r.expectedDate() == null ? "—" : String.valueOf(r.expectedDate()), String.valueOf(r.lineCount()),
                    String.valueOf(r.quantityOrdered()), String.valueOf(r.quantityReceived()), money(r.totalCost()),
                    money(r.amountPaid()), money(r.amountOutstanding())));
        }
        return exportService.toXlsx("Purchase Orders", headers, rows);
    }

    private byte[] customerPaymentsXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Date", "Customer", "Amount", "Method", "Receipt", "Recorded by");
        List<List<String>> rows = new ArrayList<>();
        for (CustomerPaymentDto p : customerCreditService.paymentHistory()) {
            if (inRange(p.paidAt(), from, to)) {
                rows.add(List.of(String.valueOf(p.paidAt()), p.customerName(), money(p.amount()), orDash(p.method()), orDash(p.receiptNumber()), orDash(p.recordedByName())));
            }
        }
        return exportService.toXlsx("Customer Payments", headers, rows);
    }

    private byte[] supplierPaymentsXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Date", "Supplier", "Amount", "Receipt", "Recorded by");
        List<List<String>> rows = new ArrayList<>();
        for (SupplierPaymentDto p : supplierPaymentService.paymentHistory()) {
            if (inRange(p.paidAt(), from, to)) {
                rows.add(List.of(String.valueOf(p.paidAt()), p.supplierName(), money(p.amount()), orDash(p.receiptNumber()), orDash(p.recordedByName())));
            }
        }
        return exportService.toXlsx("Supplier Payments", headers, rows);
    }

    private byte[] stockAdjustmentsXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Date", "Product", "Qty change", "Before", "After", "Reason", "Status", "Note");
        List<List<String>> rows = new ArrayList<>();
        for (StockAdjustmentDto a : stockAdjustmentService.listAll()) {
            if (inRange(a.createdAt(), from, to)) {
                rows.add(List.of(String.valueOf(a.createdAt()), a.productName(), String.valueOf(a.quantityDelta()),
                        String.valueOf(a.stockBefore()), String.valueOf(a.stockAfter()), orDash(a.reason()), orDash(a.status()), orDash(a.note())));
            }
        }
        return exportService.toXlsx("Stock Adjustments", headers, rows);
    }

    private byte[] generalLedgerXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Account", "Date", "Entry #", "Memo / description", "Debit", "Credit", "Balance");
        List<List<String>> rows = new ArrayList<>();
        for (GeneralLedgerAccountDto a : accountingReportService.generalLedger(from, to)) {
            String label = a.accountCode() + " — " + a.accountName();
            rows.add(List.of(label, "", "", "Opening balance", "", "", money(a.openingBalance())));
            for (LedgerLineDto l : a.lines()) {
                String description = l.description() != null && !l.description().isBlank() ? l.description() : l.memo();
                rows.add(List.of("", String.valueOf(l.entryDate()), orDash(l.entryNumber()), orDash(description),
                        money(l.debit()), money(l.credit()), money(l.runningBalance())));
            }
            rows.add(List.of(label, "", "", "Closing balance", "", "", money(a.closingBalance())));
        }
        return exportService.toXlsx("General Ledger", headers, rows);
    }

    private byte[] trialBalanceXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Code", "Account", "Type", "Debit", "Credit", "Balance");
        List<List<String>> rows = new ArrayList<>();
        for (TrialBalanceRowDto r : accountingReportService.trialBalance(from, to)) {
            rows.add(List.of(r.accountCode(), r.accountName(), r.accountType().toString(), money(r.totalDebit()), money(r.totalCredit()), money(r.balance())));
        }
        return exportService.toXlsx("Trial Balance", headers, rows);
    }

    private byte[] incomeStatementXlsx(Instant from, Instant to) {
        List<String> headers = List.of("Section", "Code", "Account", "Amount");
        List<List<String>> rows = new ArrayList<>();
        ProfitAndLossDto r = accountingReportService.profitAndLoss(from, to);
        for (AccountAmountDto a : r.revenue()) {
            rows.add(List.of("Revenue", a.accountCode(), a.accountName(), money(a.amount())));
        }
        rows.add(List.of("", "", "Total revenue", money(r.totalRevenue())));
        for (AccountAmountDto a : r.expenses()) {
            rows.add(List.of("Expense", a.accountCode(), a.accountName(), money(a.amount())));
        }
        rows.add(List.of("", "", "Total expenses", money(r.totalExpenses())));
        rows.add(List.of("", "", "Net income", money(r.netIncome())));
        return exportService.toXlsx("Income Statement", headers, rows);
    }

    private byte[] balanceSheetXlsx(Instant asOf) {
        List<String> headers = List.of("Section", "Code", "Account", "Amount");
        List<List<String>> rows = new ArrayList<>();
        BalanceSheetDto r = accountingReportService.balanceSheet(asOf);
        for (AccountAmountDto a : r.assets()) {
            rows.add(List.of("Asset", a.accountCode(), a.accountName(), money(a.amount())));
        }
        rows.add(List.of("", "", "Total assets", money(r.totalAssets())));
        for (AccountAmountDto a : r.liabilities()) {
            rows.add(List.of("Liability", a.accountCode(), a.accountName(), money(a.amount())));
        }
        rows.add(List.of("", "", "Total liabilities", money(r.totalLiabilities())));
        for (AccountAmountDto a : r.equity()) {
            rows.add(List.of("Equity", a.accountCode(), a.accountName(), money(a.amount())));
        }
        rows.add(List.of("", "", "Current earnings (not yet closed)", money(r.currentEarnings())));
        rows.add(List.of("", "", "Total equity", money(r.totalEquity().add(r.currentEarnings()))));
        return exportService.toXlsx("Balance Sheet", headers, rows);
    }

    private static boolean inRange(Instant value, Instant from, Instant toExclusive) {
        return value != null && !value.isBefore(from) && value.isBefore(toExclusive);
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
