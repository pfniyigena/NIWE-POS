package com.posapp.reports;

import com.posapp.reports.dto.BrandProfitRow;
import com.posapp.reports.dto.BrandSalesRow;
import com.posapp.reports.dto.CategoryProfitRow;
import com.posapp.reports.dto.CategorySalesRow;
import com.posapp.reports.dto.CreditSaleRow;
import com.posapp.reports.dto.CustomerStatementRow;
import com.posapp.reports.dto.DailyCreditCollectionRow;
import com.posapp.reports.dto.OutstandingCreditRow;
import com.posapp.reports.dto.OutstandingPayableRow;
import com.posapp.reports.dto.OverdueCreditRow;
import com.posapp.reports.dto.ProductProfitRow;
import com.posapp.reports.dto.ProductSalesRow;
import com.posapp.reports.dto.PurchaseOrderReportRow;
import com.posapp.reports.dto.RestaurantCancelledOrderRow;
import com.posapp.reports.dto.RestaurantMenuCategoryProfitRow;
import com.posapp.reports.dto.RestaurantMenuCategorySalesRow;
import com.posapp.reports.dto.RestaurantMenuItemProfitRow;
import com.posapp.reports.dto.RestaurantMenuItemSalesRow;
import com.posapp.reports.dto.RestaurantOrderTransactionRow;
import com.posapp.reports.dto.RestaurantOrderTypeSalesRow;
import com.posapp.reports.dto.RestaurantServerSalesRow;
import com.posapp.reports.dto.RestaurantWastageReportRow;
import com.posapp.reports.dto.SalesOrderReportRow;
import com.posapp.reports.dto.ShiftProfitRow;
import com.posapp.reports.dto.ShiftSalesRow;
import com.posapp.reports.dto.ShiftTransactionRow;
import com.posapp.reports.dto.StockMovementRow;
import com.posapp.reports.dto.StockAgingRow;
import com.posapp.reports.dto.StockValuationRow;
import com.posapp.reports.dto.SupplierStatementRow;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * The seven POS Reports (Task: "Add POS Report ... by click on each report it will open report
 * with different filters and export as EXCEL,PDF"). ADMIN-only end to end, same as
 * DashboardController — reports surface revenue/cost figures no cashier role needs. Every
 * endpoint shares the same {@code from}/{@code to}/{@code registerId}/{@code format} query
 * params (plus {@code categoryId} on the two per-product reports); {@code format} is {@code json}
 * (default, for the on-screen table), {@code xlsx}, or {@code pdf} (for download — see
 * ReportExportService).
 * <p>
 * The Credit reports and, later, Task 1's two Inventory reports below ({@code stock-valuation}/
 * {@code stock-movement}) grew this well past "seven" without anyone renaming the class javadoc —
 * still true of the shared {@code from}/{@code to}/{@code format} shape, just not of the count.
 * The two inventory endpoints drop {@code registerId} (stock isn't tied to a register), and
 * {@code stock-valuation} drops {@code from}/{@code to} too — it's a snapshot, like the two credit
 * reports above it.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW')")
public class ReportController {

    private final ReportService reportService;
    private final PurchaseReportService purchaseReportService;
    private final SalesReportService salesReportService;
    private final AssetReportService assetReportService;
    private final HrReportService hrReportService;
    private final InstallmentReportService installmentReportService;
    private final SubscriptionReportService subscriptionReportService;
    private final HelpDeskReportService helpDeskReportService;
    private final ExpenseReportService expenseReportService;
    private final TimeReportService timeReportService;
    private final WorkOrderReportService workOrderReportService;
    private final BookingReportService bookingReportService;
    private final LawReportService lawReportService;
    private final ReportExportService exportService;
    private final com.posapp.tracking.TrackingService trackingService;

    public ReportController(ReportService reportService, PurchaseReportService purchaseReportService, SalesReportService salesReportService, AssetReportService assetReportService, HrReportService hrReportService, InstallmentReportService installmentReportService, SubscriptionReportService subscriptionReportService, HelpDeskReportService helpDeskReportService, ExpenseReportService expenseReportService, TimeReportService timeReportService, WorkOrderReportService workOrderReportService, BookingReportService bookingReportService, LawReportService lawReportService, ReportExportService exportService, com.posapp.tracking.TrackingService trackingService) {
        this.reportService = reportService;
        this.purchaseReportService = purchaseReportService;
        this.salesReportService = salesReportService;
        this.assetReportService = assetReportService;
        this.hrReportService = hrReportService;
        this.installmentReportService = installmentReportService;
        this.subscriptionReportService = subscriptionReportService;
        this.helpDeskReportService = helpDeskReportService;
        this.expenseReportService = expenseReportService;
        this.timeReportService = timeReportService;
        this.workOrderReportService = workOrderReportService;
        this.bookingReportService = bookingReportService;
        this.lawReportService = lawReportService;
        this.exportService = exportService;
        this.trackingService = trackingService;
    }

    /** Every batch/lot still holding stock, soonest expiry first. */
    @GetMapping("/batch-stock")
    public ResponseEntity<?> batchStock(@RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(defaultValue = "json") String format) {
        return batchResponse("Batch Stock", trackingService.batchReport(categoryId, warehouseId, null), format);
    }

    /** Batches already expired or expiring within {@code days} days (default 60). */
    @GetMapping("/expiring-stock")
    public ResponseEntity<?> expiringStock(@RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestParam(defaultValue = "60") int days,
                                            @RequestParam(defaultValue = "json") String format) {
        return batchResponse("Expiring Stock", trackingService.batchReport(categoryId, warehouseId, Math.max(days, 0)), format);
    }

    private ResponseEntity<?> batchResponse(String title, List<com.posapp.tracking.dto.BatchReportRow> rows, String format) {
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Warehouse", "Location", "Batch", "Expiry", "Days left", "Qty", "Unit cost", "Value", "Status");
            List<List<String>> body = new ArrayList<>();
            for (com.posapp.tracking.dto.BatchReportRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.productName(), r.warehouseName(), r.locationName(), orDash(r.batchNumber()), r.expiryDate(),
                        r.daysToExpiry(), String.valueOf(r.quantity()), money(r.unitCost()), money(r.stockValue()), r.status()));
            }
            return export(title, headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Serial register: where every serial-numbered unit is, or when and on what it left. {@code status} = IN_STOCK, IN_TRANSIT, SOLD or REMOVED. */
    @GetMapping("/serial-register")
    public ResponseEntity<?> serialRegister(@RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long warehouseId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "json") String format) {
        List<com.posapp.tracking.dto.SerialReportRow> rows = trackingService.serialReport(categoryId, warehouseId, status);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Serial", "Status", "Warehouse", "Location", "Received", "Source", "Left on", "Left on document");
            List<List<String>> body = new ArrayList<>();
            for (com.posapp.tracking.dto.SerialReportRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.productName(), r.serialNumber(), r.status(), r.warehouseName(), r.locationName(),
                        r.receivedOn(), r.source(), r.leftOn(), r.leftRef()));
            }
            return export("Serial Register", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/category-sales")
    public ResponseEntity<?> categorySales(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long registerId,
                                            @RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "json") String format) {
        List<CategorySalesRow> rows = reportService.categorySales(start(from), end(to), registerId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Qty sold", "Sales total", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (CategorySalesRow r : rows) {
                body.add(List.of(r.categoryName(), String.valueOf(r.quantitySold()), money(r.salesTotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("Total POS Categories Sales", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** "Total POS Brands Sales" (Fourier: "Add also report related to brand") — mirrors category-sales exactly, one level over. */
    @GetMapping("/brand-sales")
    public ResponseEntity<?> brandSales(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @RequestParam(required = false) Long registerId,
                                         @RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "json") String format) {
        List<BrandSalesRow> rows = reportService.brandSales(start(from), end(to), registerId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Brand", "Qty sold", "Sales total", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (BrandSalesRow r : rows) {
                body.add(List.of(r.brandName(), String.valueOf(r.quantitySold()), money(r.salesTotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("Total POS Brands Sales", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/product-sales")
    public ResponseEntity<?> productSales(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(required = false) Long registerId,
                                           @RequestParam(required = false) Long branchId,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(defaultValue = "json") String format) {
        List<ProductSalesRow> rows = reportService.productSales(start(from), end(to), registerId, branchId, categoryId);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Category", "Qty sold", "Sales total", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (ProductSalesRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.productName(), r.categoryName(), String.valueOf(r.quantitySold()), money(r.salesTotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("Total POS Products Sales", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/shift-sales")
    public ResponseEntity<?> shiftSales(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @RequestParam(required = false) Long registerId,
                                         @RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "json") String format) {
        List<ShiftSalesRow> rows = reportService.shiftSales(start(from), end(to), registerId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Shift", "Register", "Opened by", "Opened at", "Status", "Sales", "Subtotal", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (ShiftSalesRow r : rows) {
                body.add(List.of("#" + r.shiftId(), r.registerName(), r.openedByName(), String.valueOf(r.openedAt()), r.status(), String.valueOf(r.saleCount()), money(r.subtotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("POS Shifts Sales", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/shift-transactions")
    public ResponseEntity<?> shiftTransactions(@RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(required = false) Long registerId,
                                                @RequestParam(required = false) Long branchId,
                                                @RequestParam(defaultValue = "json") String format) {
        List<ShiftTransactionRow> rows = reportService.shiftTransactions(start(from), end(to), registerId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Invoice", "Shift", "Register", "Cashier", "Customer", "Completed at", "Items", "Subtotal", "Tax", "Grand total", "Payment");
            List<List<String>> body = new ArrayList<>();
            for (ShiftTransactionRow r : rows) {
                body.add(List.of(orDash(r.invoiceNumber()), "#" + r.shiftId(), r.registerName(), r.cashierName(), r.customerName(), String.valueOf(r.completedAt()), String.valueOf(r.itemCount()), money(r.subtotal()), money(r.taxTotal()), money(r.grandTotal()), r.paymentMethods()));
            }
            return export("Detailed POS Shifts Transactions", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/shift-profit")
    public ResponseEntity<?> shiftProfit(@RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to,
                                          @RequestParam(required = false) Long registerId,
                                          @RequestParam(required = false) Long branchId,
                                          @RequestParam(defaultValue = "json") String format) {
        List<ShiftProfitRow> rows = reportService.shiftProfit(start(from), end(to), registerId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Shift", "Register", "Opened by", "Opened at", "Sales", "Revenue", "Cost", "Profit", "Margin %");
            List<List<String>> body = new ArrayList<>();
            for (ShiftProfitRow r : rows) {
                body.add(List.of("#" + r.shiftId(), r.registerName(), r.openedByName(), String.valueOf(r.openedAt()), String.valueOf(r.saleCount()), money(r.revenue()), money(r.cost()), money(r.profit()), r.marginPercent() + "%"));
            }
            return export("POS Shifts Profit", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/category-profit")
    public ResponseEntity<?> categoryProfit(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) Long registerId,
                                             @RequestParam(required = false) Long branchId,
                                             @RequestParam(defaultValue = "json") String format) {
        List<CategoryProfitRow> rows = reportService.categoryProfit(start(from), end(to), registerId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Qty sold", "Revenue", "Cost", "Profit", "Margin %");
            List<List<String>> body = new ArrayList<>();
            for (CategoryProfitRow r : rows) {
                body.add(List.of(r.categoryName(), String.valueOf(r.quantitySold()), money(r.revenue()), money(r.cost()), money(r.profit()), r.marginPercent() + "%"));
            }
            return export("Total POS Categories Profit", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** "Total POS Brands Profit" (Fourier: "Add also report related to brand") — mirrors category-profit exactly, one level over. */
    @GetMapping("/brand-profit")
    public ResponseEntity<?> brandProfit(@RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to,
                                          @RequestParam(required = false) Long registerId,
                                          @RequestParam(required = false) Long branchId,
                                          @RequestParam(defaultValue = "json") String format) {
        List<BrandProfitRow> rows = reportService.brandProfit(start(from), end(to), registerId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Brand", "Qty sold", "Revenue", "Cost", "Profit", "Margin %");
            List<List<String>> body = new ArrayList<>();
            for (BrandProfitRow r : rows) {
                body.add(List.of(r.brandName(), String.valueOf(r.quantitySold()), money(r.revenue()), money(r.cost()), money(r.profit()), r.marginPercent() + "%"));
            }
            return export("Total POS Brands Profit", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/product-profit")
    public ResponseEntity<?> productProfit(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long registerId,
                                            @RequestParam(required = false) Long branchId,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(defaultValue = "json") String format) {
        List<ProductProfitRow> rows = reportService.productProfit(start(from), end(to), registerId, branchId, categoryId);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Category", "Qty sold", "Revenue", "Cost", "Profit", "Margin %");
            List<List<String>> body = new ArrayList<>();
            for (ProductProfitRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.productName(), r.categoryName(), String.valueOf(r.quantitySold()), money(r.revenue()), money(r.cost()), money(r.profit()), r.marginPercent() + "%"));
            }
            return export("Total POS Products Profit", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /**
     * The Customer Statement (Task: "Add Customers Report (Customer Statement)") — unlike every
     * other report here, {@code customerId} is required, not optional: there's no "all customers"
     * statement, one is always for a specific customer. Otherwise the exact same
     * from/to/format shape as every other report endpoint.
     */
    @GetMapping("/customer-statement")
    public ResponseEntity<?> customerStatement(@RequestParam Long customerId,
                                                @RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(defaultValue = "json") String format) {
        List<CustomerStatementRow> rows = reportService.customerStatement(customerId, start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Invoice", "Date", "Register", "Cashier", "Items", "Subtotal", "Tax", "Grand total", "Payment", "Credit amount", "Due date", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (CustomerStatementRow r : rows) {
                body.add(List.of(orDash(r.invoiceNumber()), String.valueOf(r.completedAt()), r.registerName(), r.cashierName(), String.valueOf(r.itemCount()), money(r.subtotal()), money(r.taxTotal()), money(r.grandTotal()), r.paymentMethods(), money(r.creditAmount()), orDash(r.dueDate() == null ? null : r.dueDate().toString()), money(r.amountOutstanding())));
            }
            return export("Customer Statement", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Task 16: "Credit sale report" — every completed credit sale, any customer, in the date range. */
    @GetMapping("/credit-sales")
    public ResponseEntity<?> creditSales(@RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to,
                                          @RequestParam(required = false) Long branchId,
                                          @RequestParam(defaultValue = "json") String format) {
        List<CreditSaleRow> rows = reportService.creditSales(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Invoice", "Date", "Customer", "Credit amount", "Outstanding", "Due date");
            List<List<String>> body = new ArrayList<>();
            for (CreditSaleRow r : rows) {
                body.add(List.of(orDash(r.invoiceNumber()), String.valueOf(r.completedAt()), r.customerName(), money(r.creditAmount()), money(r.amountOutstanding()), orDash(r.dueDate() == null ? null : r.dueDate().toString())));
            }
            return export("Credit Sale Report", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Task 17: "Daily credit collection report" — every customer payment recorded, summed per day, in the date range. */
    @GetMapping("/daily-credit-collection")
    public ResponseEntity<?> dailyCreditCollection(@RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to,
                                                    @RequestParam(required = false) Long branchId,
                                                    @RequestParam(defaultValue = "json") String format) {
        List<DailyCreditCollectionRow> rows = reportService.dailyCreditCollection(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Payments", "Total collected");
            List<List<String>> body = new ArrayList<>();
            for (DailyCreditCollectionRow r : rows) {
                body.add(List.of(String.valueOf(r.date()), String.valueOf(r.paymentCount()), money(r.totalCollected())));
            }
            return export("Daily Credit Collection Report", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Task 10: "Outstanding credit report" — snapshot, no date range. */
    @GetMapping("/outstanding-credit")
    public ResponseEntity<?> outstandingCredit(@RequestParam(required = false) Long branchId,
                                                @RequestParam(defaultValue = "json") String format) {
        List<OutstandingCreditRow> rows = reportService.outstandingCredit(branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Customer", "Phone", "Credit limit", "Outstanding", "Available credit");
            List<List<String>> body = new ArrayList<>();
            for (OutstandingCreditRow r : rows) {
                body.add(List.of(r.customerName(), orDash(r.phone()), money(r.creditLimit()), money(r.outstandingBalance()), money(r.availableCredit())));
            }
            return export("Outstanding Credit Report", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Task 11: "Overdue credit report" — snapshot, no date range. */
    @GetMapping("/overdue-credit")
    public ResponseEntity<?> overdueCredit(@RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "json") String format) {
        List<OverdueCreditRow> rows = reportService.overdueCredit(branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Invoice", "Customer", "Phone", "Due date", "Days overdue", "Credit amount", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (OverdueCreditRow r : rows) {
                body.add(List.of(orDash(r.invoiceNumber()), r.customerName(), orDash(r.phone()), String.valueOf(r.dueDate()), String.valueOf(r.daysOverdue()), money(r.creditAmount()), money(r.amountOutstanding())));
            }
            return export("Overdue Credit Report", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Stock aging: sellable stock by how long since it last sold. See ReportService#stockAging. */
    @GetMapping("/stock-aging")
    public ResponseEntity<?> stockAging(@RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "json") String format) {
        return agingResponse("Stock Aging", reportService.stockAging(categoryId, warehouseId, branchId, null), format);
    }

    /** Dead stock: stock with no sale in the last {@code days} days (default 90) and nothing received in that window. */
    @GetMapping("/dead-stock")
    public ResponseEntity<?> deadStock(@RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) Long warehouseId,
                                        @RequestParam(required = false) Long branchId,
                                        @RequestParam(defaultValue = "90") int days,
                                        @RequestParam(defaultValue = "json") String format) {
        return agingResponse("Dead Stock", reportService.stockAging(categoryId, warehouseId, branchId, Math.max(days, 1)), format);
    }

    private ResponseEntity<?> agingResponse(String title, List<StockAgingRow> rows, String format) {
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Category", "Qty", "Unit cost", "Stock value", "Last received", "Last sold", "Days since sale", "Age");
            List<List<String>> body = new ArrayList<>();
            for (StockAgingRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.productName(), r.categoryName(), String.valueOf(r.quantity()), money(r.unitCost()), money(r.stockValue()),
                        orDash(r.lastReceived()), orDash(r.lastSold()), r.daysSinceLastSale(), r.ageBucket()));
            }
            return export(title, headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Task 1: "Inventory Report" — "Stock on hand & valuation" half. Snapshot, no date range, same shape as Outstanding/Overdue Credit above. Multi-warehouse phase 1 added {@code warehouseId} (one warehouse) and {@code allWarehouses} (sum of every warehouse) — see ReportService#stockValuation. */
    @GetMapping("/stock-valuation")
    public ResponseEntity<?> stockValuation(@RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long warehouseId,
                                             @RequestParam(defaultValue = "false") boolean allWarehouses,
                                             @RequestParam(required = false) Long branchId,
                                             @RequestParam(defaultValue = "json") String format) {
        List<StockValuationRow> rows = reportService.stockValuation(categoryId, warehouseId, allWarehouses, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Category", "Stock qty", "Reserved", "In transit", "Unit cost", "Stock value", "Status");
            List<List<String>> body = new ArrayList<>();
            for (StockValuationRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.productName(), r.categoryName(), String.valueOf(r.stockQuantity()), String.valueOf(r.reserved()), String.valueOf(r.inTransit()), money(r.unitCost()), money(r.stockValue()), r.status()));
            }
            return export("Stock On Hand And Valuation", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /**
     * Task 1: "Inventory Report" — "Stock movement history" half. Date range + optional category,
     * no register (movements aren't tied to one). Fourier: "can we have Stock Movement History for
     * a specific product" added {@code productId} — a second, independent narrowing filter,
     * alongside {@code categoryId} rather than replacing it, so either or both can be set.
     */
    @GetMapping("/stock-movement")
    public ResponseEntity<?> stockMovement(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Long productId,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "json") String format) {
        List<StockMovementRow> rows = reportService.stockMovementHistory(start(from), end(to), categoryId, productId, warehouseId, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Type", "SKU", "Product", "Category", "Warehouse", "Qty change", "Before", "After", "Reference", "Note");
            List<List<String>> body = new ArrayList<>();
            for (StockMovementRow r : rows) {
                body.add(List.of(String.valueOf(r.occurredAt()), r.type(), orDash(r.sku()), r.productName(), r.categoryName(), orDash(r.warehouseName()), String.valueOf(r.quantityDelta()), String.valueOf(r.stockBefore()), String.valueOf(r.stockAfter()), orDash(r.reference()), orDash(r.note())));
            }
            return export("Stock Movement History", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Task 4: "Add Purchase order reports." Date range only — no register (not tied to one), no category (a PO can span several). */
    @GetMapping("/purchase-orders")
    public ResponseEntity<?> purchaseOrders(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) Long branchId,
                                             @RequestParam(defaultValue = "json") String format) {
        List<PurchaseOrderReportRow> rows = reportService.purchaseOrders(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Order #", "Supplier", "Status", "Order date", "Expected", "Lines", "Qty ordered", "Qty received", "Total cost", "Paid", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (PurchaseOrderReportRow r : rows) {
                body.add(List.of(orDash(r.orderNumber()), orDash(r.supplierName()), r.status(), String.valueOf(r.orderDate()),
                        r.expectedDate() == null ? "—" : String.valueOf(r.expectedDate()), String.valueOf(r.lineCount()),
                        String.valueOf(r.quantityOrdered()), String.valueOf(r.quantityReceived()), money(r.totalCost()),
                        money(r.amountPaid()), money(r.amountOutstanding())));
            }
            return export("Purchase Orders", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /**
     * Task 5: "Add Supplier Statement" — unlike every report above, {@code supplierId} is
     * required, not optional: there's no "all suppliers" statement, one is always for a specific
     * supplier, same shape as {@link #customerStatement}.
     */
    @GetMapping("/supplier-statement")
    public ResponseEntity<?> supplierStatement(@RequestParam Long supplierId,
                                                @RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(defaultValue = "json") String format) {
        List<SupplierStatementRow> rows = reportService.supplierStatement(supplierId, start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Order #", "Status", "Order date", "Expected", "Lines", "Qty ordered", "Qty received", "Total cost", "Paid", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (SupplierStatementRow r : rows) {
                body.add(List.of(orDash(r.orderNumber()), r.status(), String.valueOf(r.orderDate()),
                        r.expectedDate() == null ? "—" : String.valueOf(r.expectedDate()), String.valueOf(r.lineCount()),
                        String.valueOf(r.quantityOrdered()), String.valueOf(r.quantityReceived()), money(r.totalCost()),
                        money(r.amountPaid()), money(r.amountOutstanding())));
            }
            return export("Supplier Statement", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Task: "Add Supplier payment" — snapshot, no date range, the accounts-payable mirror of {@link #outstandingCredit}. */
    @GetMapping("/outstanding-payables")
    public ResponseEntity<?> outstandingPayables(@RequestParam(defaultValue = "json") String format) {
        List<OutstandingPayableRow> rows = reportService.outstandingPayables();
        if (isDownload(format)) {
            List<String> headers = List.of("Supplier", "Phone", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (OutstandingPayableRow r : rows) {
                body.add(List.of(r.supplierName(), orDash(r.phone()), money(r.outstandingBalance())));
            }
            return export("Outstanding Payables Report", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Purchases reports (Purchases > Reports) — see PurchaseReportService. Date range (+ branch) only, like purchase-orders above. ----

    @GetMapping("/purchases-by-supplier")
    public ResponseEntity<?> purchasesBySupplier(@RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(required = false) Long branchId,
                                                 @RequestParam(defaultValue = "json") String format) {
        var rows = purchaseReportService.purchasesBySupplier(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Supplier", "Orders", "Qty ordered", "Qty received", "Net cost", "Tax", "Total cost", "Paid", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.supplierName()), String.valueOf(r.orderCount()), String.valueOf(r.quantityOrdered()),
                        String.valueOf(r.quantityReceived()), money(r.netCost()), money(r.taxCost()), money(r.totalCost()),
                        money(r.amountPaid()), money(r.amountOutstanding())));
            }
            return export("Purchases by Supplier", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/purchases-by-product")
    public ResponseEntity<?> purchasesByProduct(@RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(required = false) Long branchId,
                                                @RequestParam(defaultValue = "json") String format) {
        var rows = purchaseReportService.purchasesByProduct(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Category", "Qty ordered", "Qty received", "Net cost", "Tax", "Total cost", "Avg unit cost");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.sku()), orDash(r.productName()), orDash(r.categoryName()), String.valueOf(r.quantityOrdered()),
                        String.valueOf(r.quantityReceived()), money(r.netCost()), money(r.taxCost()), money(r.totalCost()), money(r.averageUnitCost())));
            }
            return export("Purchases by Product", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/purchases-by-category")
    public ResponseEntity<?> purchasesByCategory(@RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(required = false) Long branchId,
                                                 @RequestParam(defaultValue = "json") String format) {
        var rows = purchaseReportService.purchasesByCategory(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Qty ordered", "Qty received", "Net cost", "Tax", "Total cost");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.categoryName()), String.valueOf(r.quantityOrdered()), String.valueOf(r.quantityReceived()),
                        money(r.netCost()), money(r.taxCost()), money(r.totalCost())));
            }
            return export("Purchases by Category", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/purchase-bills")
    public ResponseEntity<?> purchaseBills(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(required = false) Long branchId,
                                           @RequestParam(defaultValue = "json") String format) {
        var rows = purchaseReportService.bills(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Bill #", "Purchase order", "Supplier", "Status", "Bill date", "Total", "Paid", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.billNumber()), orDash(r.purchaseOrderNumber()), orDash(r.supplierName()), r.status(),
                        String.valueOf(r.billDate()), money(r.totalAmount()), money(r.amountPaid()), money(r.amountOutstanding())));
            }
            return export("Bills", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/purchase-returns")
    public ResponseEntity<?> purchaseReturns(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) Long branchId,
                                             @RequestParam(defaultValue = "json") String format) {
        var rows = purchaseReportService.purchaseReturns(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Return #", "Purchase order", "Supplier", "Return date", "Reason", "Qty", "Refund");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.returnNumber()), orDash(r.purchaseOrderNumber()), orDash(r.supplierName()),
                        String.valueOf(r.returnDate()), orDash(r.reason()), String.valueOf(r.quantity()), money(r.refundTotal())));
            }
            return export("Purchase Returns", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/supplier-payments")
    public ResponseEntity<?> supplierPayments(@RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(defaultValue = "json") String format) {
        var rows = purchaseReportService.supplierPayments(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Receipt #", "Supplier", "Paid at", "Method", "Amount", "Note");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.receiptNumber()), orDash(r.supplierName()), String.valueOf(r.paidAt()), orDash(r.method()),
                        money(r.amount()), orDash(r.note())));
            }
            return export("Supplier Payments", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Snapshot, no date range — what is owed to each supplier today, by age. */
    @GetMapping("/payables-aging")
    public ResponseEntity<?> payablesAging(@RequestParam(defaultValue = "json") String format) {
        var rows = purchaseReportService.payablesAging();
        if (isDownload(format)) {
            List<String> headers = List.of("Supplier", "0-30 days", "31-60 days", "61-90 days", "Over 90 days", "Total outstanding");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.supplierName()), money(r.days0To30()), money(r.days31To60()), money(r.days61To90()),
                        money(r.over90Days()), money(r.totalOutstanding())));
            }
            return export("Payables Aging", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }


    // ---- Assets reports (Assets > Reports) — see AssetReportService. The register and the category totals are snapshots (no dates). ----

    @GetMapping("/asset-register")
    public ResponseEntity<?> assetRegister(@RequestParam(required = false) Long branchId,
                                           @RequestParam(defaultValue = "json") String format) {
        var rows = assetReportService.register(branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Tag", "Asset", "Category", "Status", "Purchased", "Cost", "Accumulated depreciation", "Book value", "Branch", "Location", "Assigned to", "Serial no.");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(r.code(), r.name(), r.categoryName(), r.status(), String.valueOf(r.purchaseDate()), money(r.cost()),
                        money(r.accumulatedDepreciation()), money(r.bookValue()), orDash(r.branchName()), orDash(r.location()), orDash(r.assignedTo()), orDash(r.serialNumber())));
            }
            return export("Asset Register", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/assets-by-category")
    public ResponseEntity<?> assetsByCategory(@RequestParam(required = false) Long branchId,
                                              @RequestParam(defaultValue = "json") String format) {
        var rows = assetReportService.byCategory(branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Assets", "Cost", "Accumulated depreciation", "Book value");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.categoryName(), String.valueOf(r.assetCount()), money(r.cost()), money(r.accumulatedDepreciation()), money(r.bookValue())));
            }
            return export("Assets by Category", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/depreciation-schedule")
    public ResponseEntity<?> depreciationSchedule(@RequestParam(required = false) LocalDate from,
                                                  @RequestParam(required = false) LocalDate to,
                                                  @RequestParam(required = false) Long branchId,
                                                  @RequestParam(defaultValue = "json") String format) {
        var rows = assetReportService.depreciationSchedule(assetFrom(from), assetTo(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Month end", "Tag", "Asset", "Category", "Branch", "Depreciation", "Accumulated after", "Book value after");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.periodEnd()), r.code(), r.name(), r.categoryName(), orDash(r.branchName()), money(r.amount()),
                        money(r.accumulatedAfter()), money(r.bookValueAfter())));
            }
            return export("Depreciation Schedule", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/asset-disposals")
    public ResponseEntity<?> assetDisposals(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "json") String format) {
        var rows = assetReportService.disposals(assetFrom(from), assetTo(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Disposed", "Tag", "Asset", "Category", "Branch", "Cost", "Accumulated depreciation", "Book value", "Proceeds", "Gain / (loss)");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.disposalDate()), r.code(), r.name(), r.categoryName(), orDash(r.branchName()), money(r.cost()),
                        money(r.accumulatedDepreciation()), money(r.bookValue()), money(r.proceeds()), money(r.gainLoss())));
            }
            return export("Asset Disposals", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/asset-transfers")
    public ResponseEntity<?> assetTransfers(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "json") String format) {
        var rows = assetReportService.transfers(assetFrom(from), assetTo(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Tag", "Asset", "From branch", "To branch", "From location", "To location", "Reason");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(String.valueOf(r.transferDate()), r.code(), r.name(), orDash(r.fromBranch()), orDash(r.toBranch()),
                        orDash(r.fromLocation()), orDash(r.toLocation()), orDash(r.reason())));
            }
            return export("Asset Transfers", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- HR reports (HR > Reports) — see HrReportService. Payroll and contract reports need the Payroll area; the rest need the HR area. ----

    private static final String EXPENSE_VIEW = "hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW') or hasAuthority('AREA_ACCOUNTING_VIEW')";
    private static final String HELPDESK_VIEW = "hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW') or hasAuthority('AREA_OPERATIONS_VIEW')";
    private static final String HR_VIEW = "hasRole('ADMIN') or hasAuthority('AREA_HR_VIEW')";
    private static final String PAYROLL_VIEW = "hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')";

    @PreAuthorize(HR_VIEW)
    @GetMapping("/employee-directory")
    public ResponseEntity<?> employeeDirectory(@RequestParam(defaultValue = "json") String format) {
        var rows = hrReportService.directory(null, null);
        if (isDownload(format)) {
            List<String> headers = List.of("No.", "Name", "Job title", "Department", "Reports to", "Branch", "Status", "Hired", "Phone", "Email");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(r.employeeNumber(), r.name(), orDash(r.jobTitle()), orDash(r.department()), orDash(r.manager()),
                        orDash(r.branch()), r.status(), String.valueOf(r.hireDate()), orDash(r.phone()), orDash(r.email())));
            }
            return export("Employee Directory", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HR_VIEW)
    @GetMapping("/headcount-by-department")
    public ResponseEntity<?> headcountByDepartment(@RequestParam(defaultValue = "json") String format) {
        var rows = hrReportService.headcount();
        if (isDownload(format)) {
            List<String> headers = List.of("Department", "Active", "Inactive", "Left", "Total");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.department(), String.valueOf(r.active()), String.valueOf(r.inactive()), String.valueOf(r.left()), String.valueOf(r.total())));
            }
            return export("Headcount by Department", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HR_VIEW)
    @GetMapping("/leave-balances")
    public ResponseEntity<?> leaveBalances(@RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        int year = (to != null ? to : LocalDate.now(ZoneOffset.UTC)).getYear();
        var rows = hrReportService.leaveBalances(year);
        if (isDownload(format)) {
            List<String> headers = List.of("No.", "Employee", "Leave type", "Entitled (" + year + ")", "Used", "Pending", "Remaining");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.employeeNumber(), r.employee(), r.leaveType(), r.entitled(), r.used().stripTrailingZeros().toPlainString(),
                        r.pending().stripTrailingZeros().toPlainString(), r.remaining()));
            }
            return export("Leave Balances " + year, headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HR_VIEW)
    @GetMapping("/leave-requests")
    public ResponseEntity<?> leaveRequestsReport(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(defaultValue = "json") String format) {
        var rows = hrReportService.leaveRequests(assetFrom(from), assetTo(to));
        if (isDownload(format)) {
            List<String> headers = List.of("No.", "Employee", "Leave type", "From", "To", "Days", "Status", "Decided by", "Reason");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(r.employeeNumber(), r.employee(), r.leaveType(), String.valueOf(r.startDate()), String.valueOf(r.endDate()),
                        r.days().stripTrailingZeros().toPlainString(), r.status(), orDash(r.decidedBy()), orDash(r.reason())));
            }
            return export("Leave Requests", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HR_VIEW)
    @GetMapping("/attendance-summary")
    public ResponseEntity<?> attendanceSummary(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                               @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = hrReportService.attendance(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("No.", "Employee", "Department", "Expected days", "Present", "Late", "Absent", "Leave days", "Hours", "Overtime hours");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(r.employeeNumber(), r.employee(), orDash(r.department()), String.valueOf(r.expectedDays()),
                        String.valueOf(r.presentDays()), String.valueOf(r.lateDays()), String.valueOf(r.absentDays()),
                        r.leaveDays().stripTrailingZeros().toPlainString(), r.hoursWorked().toPlainString(), r.overtimeHours().toPlainString()));
            }
            return export("Attendance Summary", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(PAYROLL_VIEW)
    @GetMapping("/contracts-expiring")
    public ResponseEntity<?> contractsExpiring(@RequestParam(required = false) Integer days, @RequestParam(defaultValue = "json") String format) {
        var rows = hrReportService.contractsExpiring(days != null ? days : 60);
        if (isDownload(format)) {
            List<String> headers = List.of("Contract", "Employee", "Type", "Start", "End", "Days left");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(r.contractNumber(), r.employee(), r.type(), String.valueOf(r.startDate()), String.valueOf(r.endDate()),
                        r.daysLeft() != null ? String.valueOf(r.daysLeft()) : "—"));
            }
            return export("Contracts Expiring", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(PAYROLL_VIEW)
    @GetMapping("/payroll-register")
    public ResponseEntity<?> payrollRegister(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                             @RequestParam(defaultValue = "json") String format) {
        var rows = hrReportService.payrollRegister(assetFrom(from), assetTo(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Period", "No.", "Employee", "Department", "Gross", "PAYE", "Employee contributions", "Other deductions", "Net pay", "Employer contributions", "Status");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(r.period(), r.employeeNumber(), r.employee(), orDash(r.department()), money(r.gross()), money(r.paye()),
                        money(r.employeeContributions()), money(r.otherDeductions()), money(r.net()), money(r.employerContributions()), r.status()));
            }
            return export("Payroll Register", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(PAYROLL_VIEW)
    @GetMapping("/payroll-summary")
    public ResponseEntity<?> payrollSummary(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                            @RequestParam(defaultValue = "json") String format) {
        var rows = hrReportService.payrollSummary(assetFrom(from), assetTo(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Period", "Status", "Employees", "Gross", "PAYE", "Employee contributions", "Other deductions", "Net pay", "Employer contributions", "Total cost");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(java.util.Arrays.asList(r.period(), r.status(), String.valueOf(r.employees()), money(r.gross()), money(r.paye()),
                        money(r.employeeContributions()), money(r.otherDeductions()), money(r.net()), money(r.employerContributions()), money(r.totalCost())));
            }
            return export("Payroll Summary", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Instalment reports (Installments) — see InstallmentReportService. ----

    @GetMapping("/installment-agreements")
    public ResponseEntity<?> installmentAgreements(@RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        var rows = installmentReportService.agreements(branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Plan", "Customer", "Invoice", "Status", "Instalments", "Paid", "Financed", "Interest", "Total payable", "Paid amount", "Outstanding", "Overdue", "Next due");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.planNumber(), orDash(r.customer()), orDash(r.invoiceNumber()), r.status(), String.valueOf(r.instalments()), String.valueOf(r.paidInstalments()),
                        money(r.financed()), money(r.interest()), money(r.totalPayable()), money(r.paid()), money(r.outstanding()), money(r.overdue()),
                        r.nextDue() != null ? String.valueOf(r.nextDue()) : "—"));
            }
            return export("Instalment Agreements", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/installments-due")
    public ResponseEntity<?> installmentsDue(@RequestParam(required = false) Integer days, @RequestParam(required = false) Long branchId,
                                             @RequestParam(defaultValue = "json") String format) {
        return dueResponse("Instalments Due", installmentReportService.due(days != null ? days : 30, branchId), format);
    }

    @GetMapping("/installments-overdue")
    public ResponseEntity<?> installmentsOverdue(@RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        return dueResponse("Overdue Instalments", installmentReportService.overdue(branchId), format);
    }

    private ResponseEntity<?> dueResponse(String title, List<com.posapp.reports.dto.InstallmentReportRows.DueRow> rows, String format) {
        if (isDownload(format)) {
            List<String> headers = List.of("Plan", "Customer", "Invoice", "Instalment", "Due date", "Amount due", "Paid", "Balance", "Status", "Days overdue");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.planNumber(), orDash(r.customer()), orDash(r.invoiceNumber()), r.instalment(), String.valueOf(r.dueDate()),
                        money(r.totalDue()), money(r.paid()), money(r.balance()), r.status(), String.valueOf(r.daysOverdue())));
            }
            return export(title, headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/installments-defaulted")
    public ResponseEntity<?> installmentsDefaulted(@RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        var rows = installmentReportService.defaulted(branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Plan", "Customer", "Invoice", "Status", "Since", "Days", "Financed", "Paid", "Still owed", "Written off", "Reason");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.planNumber(), orDash(r.customer()), orDash(r.invoiceNumber()), r.status(), r.since() != null ? String.valueOf(r.since()) : "—",
                        String.valueOf(r.daysSince()), money(r.financed()), money(r.paid()), money(r.outstanding()), money(r.writtenOff()), orDash(r.reason())));
            }
            return export("Defaulted and Written-off Instalment Plans", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/installment-collections")
    public ResponseEntity<?> installmentCollections(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                                    @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = installmentReportService.collections(start, end, branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Receipt", "Plan", "Customer", "Principal", "Interest and fees", "Total", "Method", "Recorded by");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.paidAt()), orDash(r.receiptNumber()), r.planNumber(), orDash(r.customer()), money(r.principal()),
                        money(r.interestAndFees()), money(r.total()), orDash(r.method()), orDash(r.recordedBy())));
            }
            return export("Instalment Collections", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Subscription reports — see SubscriptionReportService. ----

    @GetMapping("/subscriptions-by-plan")
    public ResponseEntity<?> subscriptionsByPlan(@RequestParam(defaultValue = "json") String format) {
        var rows = subscriptionReportService.byPlan();
        if (isDownload(format)) {
            List<String> headers = List.of("Plan", "Active", "On trial", "Paused", "Cancelled or ended", "Seats", "Monthly recurring", "Yearly recurring", "Average per subscription");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.plan()), String.valueOf(r.active()), String.valueOf(r.onTrial()), String.valueOf(r.paused()), String.valueOf(r.cancelledOrEnded()),
                        String.valueOf(r.seats()), money(r.mrr()), money(r.arr()), money(r.averageMonthly())));
            }
            return export("Subscriptions by Plan", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/subscriptions-churn")
    public ResponseEntity<?> subscriptionsChurn(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = subscriptionReportService.churn(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Customer", "Plan", "Status", "Started", "Left on", "Months", "Monthly value lost", "Cycles billed", "Reason");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.customer()), orDash(r.plan()), r.status(), String.valueOf(r.started()), String.valueOf(r.leftOn()), String.valueOf(r.monthsActive()),
                        money(r.monthlyValue()), String.valueOf(r.cyclesBilled()), orDash(r.reason())));
            }
            return export("Subscription Churn", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/subscriptions-unpaid")
    public ResponseEntity<?> subscriptionsUnpaid(@RequestParam(defaultValue = "json") String format) {
        var rows = subscriptionReportService.unpaid();
        if (isDownload(format)) {
            List<String> headers = List.of("Customer", "Plan", "Invoice", "Due", "Days late", "Total", "Outstanding", "Reminders sent", "Subscription");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.customer()), orDash(r.plan()), r.invoiceNumber(), String.valueOf(r.dueOn()), String.valueOf(r.daysLate()), money(r.total()),
                        money(r.outstanding()), String.valueOf(r.reminders()), orDash(r.subscriptionStatus())));
            }
            return export("Unpaid Subscription Invoices", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Help Desk reports — see HelpDeskReportService. ----

    private static String hours(Double h) {
        return h == null ? "—" : String.valueOf(h);
    }

    @PreAuthorize(HELPDESK_VIEW)
    @GetMapping("/helpdesk-sla")
    public ResponseEntity<?> helpdeskSla(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        var rows = helpDeskReportService.sla(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Priority", "Tickets", "Answered in time", "Answered late or not yet", "Avg first response (h)", "Resolved", "Resolved in time", "Resolved late or overdue", "Avg resolution (h)", "On time %");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.priority(), String.valueOf(r.tickets()), String.valueOf(r.responseMet()), String.valueOf(r.responseMissed()), hours(r.avgFirstResponseHours()),
                        String.valueOf(r.resolved()), String.valueOf(r.resolutionMet()), String.valueOf(r.resolutionMissed()), hours(r.avgResolutionHours()), hours(r.compliancePercent())));
            }
            return export("Help Desk SLA Performance", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HELPDESK_VIEW)
    @GetMapping("/helpdesk-by-agent")
    public ResponseEntity<?> helpdeskByAgent(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        var rows = helpDeskReportService.byAgent(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Agent", "Tickets", "Still open", "Resolved", "Overdue now", "Avg resolution (h)", "Avg rating");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.agent(), String.valueOf(r.assigned()), String.valueOf(r.open()), String.valueOf(r.resolved()), String.valueOf(r.overdue()), hours(r.avgResolutionHours()), hours(r.avgRating())));
            }
            return export("Help Desk Tickets by Agent", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HELPDESK_VIEW)
    @GetMapping("/helpdesk-by-category")
    public ResponseEntity<?> helpdeskByCategory(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        var rows = helpDeskReportService.byCategory(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Tickets", "Still open", "Resolved", "Avg resolution (h)", "Avg rating");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.category(), String.valueOf(r.tickets()), String.valueOf(r.open()), String.valueOf(r.resolved()), hours(r.avgResolutionHours()), hours(r.avgRating())));
            }
            return export("Help Desk Tickets by Category", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HELPDESK_VIEW)
    @GetMapping("/helpdesk-satisfaction")
    public ResponseEntity<?> helpdeskSatisfaction(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        var rows = helpDeskReportService.satisfaction(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Ticket #", "Subject", "Requester", "Agent", "Rating", "Comment", "Rated on");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.ticketNumber(), r.subject(), orDash(r.requester()), orDash(r.agent()), r.rating() + " / 5", orDash(r.comment()), String.valueOf(r.ratedAt())));
            }
            return export("Help Desk Satisfaction", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(HELPDESK_VIEW)
    @GetMapping("/helpdesk-overdue")
    public ResponseEntity<?> helpdeskOverdue(@RequestParam(defaultValue = "json") String format) {
        var rows = helpDeskReportService.overdue();
        if (isDownload(format)) {
            List<String> headers = List.of("Ticket #", "Subject", "Priority", "Status", "Agent", "Deadline missed", "Was due", "Hours late");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.ticketNumber(), r.subject(), r.priority(), r.status(), orDash(r.agent()), r.deadline(), String.valueOf(r.due()), String.valueOf(r.hoursLate())));
            }
            return export("Help Desk Overdue Tickets", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Expenses reports — see ExpenseReportService. ----

    private static LocalDate rangeFrom(LocalDate from) {
        return from != null ? from : LocalDate.now().withDayOfMonth(1);
    }

    private static LocalDate rangeTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    @PreAuthorize(EXPENSE_VIEW)
    @GetMapping("/expense-register")
    public ResponseEntity<?> expenseRegister(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) String status, @RequestParam(defaultValue = "json") String format) {
        var rows = expenseReportService.register(rangeFrom(from), rangeTo(to), status);
        if (isDownload(format)) {
            List<String> headers = List.of("Number", "Date", "Payee", "Category", "Cost center", "Reference", "Status", "Total", "VAT", "Cost without VAT", "Paid", "Paid from", "Due date", "Still owed", "Entered by", "Approved by");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.expenseNumber(), String.valueOf(r.date()), orDash(r.payee()), orDash(r.category()), orDash(r.costCenter()), orDash(r.reference()), r.status(),
                        money(r.amount()), money(r.tax()), money(r.net()), r.paid() ? "Yes" : "No", orDash(r.paidFrom()), r.dueDate() == null ? "—" : String.valueOf(r.dueDate()),
                        money(r.balance()), orDash(r.enteredBy()), orDash(r.approvedBy())));
            }
            return export("Expense Register", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(EXPENSE_VIEW)
    @GetMapping("/expenses-unpaid-aging")
    public ResponseEntity<?> expensesUnpaidAging(@RequestParam(defaultValue = "json") String format) {
        var rows = expenseReportService.unpaidAging();
        if (isDownload(format)) {
            List<String> headers = List.of("Number", "Payee", "Reference", "Bill date", "Due date", "Days late", "Age", "Total", "Paid so far", "Still owed");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.expenseNumber(), orDash(r.payee()), orDash(r.reference()), String.valueOf(r.billDate()), String.valueOf(r.dueDate()), String.valueOf(r.daysOverdue()),
                        r.bucket(), money(r.amount()), money(r.paidSoFar()), money(r.balance())));
            }
            return export("Unpaid Bills by Age", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(EXPENSE_VIEW)
    @GetMapping("/expense-budget-vs-actual")
    public ResponseEntity<?> expenseBudgetVsActual(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        var rows = expenseReportService.budgetVsActual(rangeFrom(from), rangeTo(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Month", "Budget for", "Type", "Monthly budget", "Spent", "Waiting for approval", "Left", "Used %", "State");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.month(), r.label(), r.kind().equals("CATEGORY") ? "Category" : "Cost center", money(r.budget()), money(r.spent()), money(r.pending()), money(r.remaining()),
                        String.valueOf(r.percentUsed()), r.state()));
            }
            return export("Budget vs Actual", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @PreAuthorize(EXPENSE_VIEW)
    @GetMapping("/expenses-by-payment-account")
    public ResponseEntity<?> expensesByPaymentAccount(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "json") String format) {
        var rows = expenseReportService.byPaymentAccount(rangeFrom(from), rangeTo(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Paid from", "Paid at once", "Amount", "Bill payments", "Amount", "Total paid");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.account(), String.valueOf(r.paidAtOnce()), money(r.paidAtOnceAmount()), String.valueOf(r.billPayments()), money(r.billPaymentsAmount()), money(r.total())));
            }
            return export("Expenses by Payment Account", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Time tracking reports — see TimeReportService. ----

    private static String dec(java.math.BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString();
    }

    @GetMapping("/time-timesheets")
    public ResponseEntity<?> timeTimesheets(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                            @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = timeReportService.timesheets(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Person", "Hours", "Billable hours", "Non-billable hours", "Billable amount", "Not yet approved", "Invoiced hours");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.person(), dec(r.hours()), dec(r.billableHours()), dec(r.nonBillableHours()), money(r.billableAmount()), dec(r.pendingHours()), dec(r.invoicedHours())));
            }
            return export("Timesheet Summary", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/time-project-hours")
    public ResponseEntity<?> timeProjectHours(@RequestParam(defaultValue = "json") String format) {
        var rows = timeReportService.projectHours();
        if (isDownload(format)) {
            List<String> headers = List.of("Project", "Customer", "Status", "Budget hours", "Hours logged", "Billable hours", "% of budget", "Hours left", "Invoiced", "Not yet invoiced");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.project(), orDash(r.customer()), r.status(), dec(r.budgetHours()), dec(r.hoursLogged()), dec(r.billableHours()), dec(r.percentOfBudget()),
                        dec(r.hoursLeft()), money(r.billedAmount()), money(r.unbilledAmount())));
            }
            return export("Project Hours vs Budget", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/time-unbilled")
    public ResponseEntity<?> timeUnbilled(@RequestParam(defaultValue = "json") String format) {
        var rows = timeReportService.unbilled();
        if (isDownload(format)) {
            List<String> headers = List.of("Project", "Customer", "Entries", "Hours", "Amount", "Oldest entry");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.project(), orDash(r.customer()), String.valueOf(r.entries()), dec(r.hours()), money(r.amount()), r.oldest() != null ? String.valueOf(r.oldest()) : "—"));
            }
            return export("Unbilled Time", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/time-utilisation")
    public ResponseEntity<?> timeUtilisation(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                             @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = timeReportService.utilisation(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Person", "Available hours", "Hours logged", "Billable hours", "Utilisation %", "Billable %");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.person(), dec(r.capacityHours()), dec(r.loggedHours()), dec(r.billableHours()), dec(r.utilisationPercent()), dec(r.billablePercent())));
            }
            return export("Utilisation", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/time-entries")
    public ResponseEntity<?> timeEntries(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                         @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = timeReportService.entries(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Person", "Project", "Customer", "Task", "Description", "Hours", "Billable", "Rate", "Amount", "Status");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.date()), r.person(), r.project(), orDash(r.customer()), orDash(r.task()), orDash(r.description()), dec(r.hours()), r.billable(),
                        money(r.rate()), money(r.amount()), r.status()));
            }
            return export("Time Entries", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    private static LocalDate assetFrom(LocalDate from) {
        return from != null ? from : LocalDate.now(ZoneOffset.UTC).withDayOfYear(1);
    }

    private static LocalDate assetTo(LocalDate to) {
        return to != null ? to : LocalDate.now(ZoneOffset.UTC);
    }

    /** Task: "Add Sale order... Then let's have Sale as Menu." Date range only — no register (not tied to one), no category (an order can span several). */
    @GetMapping("/sales-orders")
    public ResponseEntity<?> salesOrders(@RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to,
                                          @RequestParam(required = false) Long branchId,
                                          @RequestParam(defaultValue = "json") String format) {
        List<SalesOrderReportRow> rows = reportService.salesOrders(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Order #", "Customer", "Status", "Order date", "Expected", "Due date", "Lines", "Qty ordered", "Qty fulfilled", "Total amount", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (SalesOrderReportRow r : rows) {
                body.add(List.of(orDash(r.orderNumber()), orDash(r.customerName()), r.status(), String.valueOf(r.orderDate()),
                        r.expectedDate() == null ? "—" : String.valueOf(r.expectedDate()),
                        r.dueDate() == null ? "—" : String.valueOf(r.dueDate()), String.valueOf(r.lineCount()),
                        String.valueOf(r.quantityOrdered()), String.valueOf(r.quantityFulfilled()), money(r.totalAmount()), money(r.amountOutstanding())));
            }
            return export("Sales Orders", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Sales reports (Sales > Reports) — see SalesReportService. Date range (+ branch) only, like sales-orders above. ----

    @GetMapping("/sales-by-customer")
    public ResponseEntity<?> salesByCustomer(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) Long branchId,
                                             @RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.salesByCustomer(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Customer", "Orders", "Qty ordered", "Qty fulfilled", "Net amount", "Tax", "Total amount", "Paid", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.customerName()), String.valueOf(r.orderCount()), String.valueOf(r.quantityOrdered()),
                        String.valueOf(r.quantityFulfilled()), money(r.netAmount()), money(r.taxAmount()), money(r.totalAmount()),
                        money(r.amountPaid()), money(r.amountOutstanding())));
            }
            return export("Sales by Customer", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/sales-by-product")
    public ResponseEntity<?> salesByProduct(@RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.salesByProduct(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Product", "Category", "Qty ordered", "Qty fulfilled", "Net amount", "Tax", "Total amount", "Avg unit price");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.sku()), orDash(r.productName()), orDash(r.categoryName()), String.valueOf(r.quantityOrdered()),
                        String.valueOf(r.quantityFulfilled()), money(r.netAmount()), money(r.taxAmount()), money(r.totalAmount()), money(r.averageUnitPrice())));
            }
            return export("Sales by Product", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/sales-by-category")
    public ResponseEntity<?> salesByCategory(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) Long branchId,
                                             @RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.salesByCategory(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Qty ordered", "Qty fulfilled", "Net amount", "Tax", "Total amount");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.categoryName()), String.valueOf(r.quantityOrdered()), String.valueOf(r.quantityFulfilled()),
                        money(r.netAmount()), money(r.taxAmount()), money(r.totalAmount())));
            }
            return export("Sales by Category", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/sales-invoices")
    public ResponseEntity<?> salesInvoices(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(required = false) Long branchId,
                                           @RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.invoices(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Invoice #", "Sale order", "Customer", "Status", "Invoice date", "Due date", "Total", "Paid", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.invoiceNumber()), orDash(r.salesOrderNumber()), orDash(r.customerName()), r.status(),
                        String.valueOf(r.invoiceDate()), r.dueDate() == null ? "—" : String.valueOf(r.dueDate()),
                        money(r.totalAmount()), money(r.amountPaid()), money(r.amountOutstanding())));
            }
            return export("Invoices", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/sales-credit-notes")
    public ResponseEntity<?> salesCreditNotes(@RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.creditNotes(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Credit note #", "Invoice #", "Customer", "Voided at", "Amount", "Reason", "Payment handling");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.creditNoteNumber()), orDash(r.invoiceNumber()), orDash(r.customerName()),
                        String.valueOf(r.voidedAt()), money(r.totalAmount()), orDash(r.reason()), orDash(r.paymentDisposition())));
            }
            return export("Credit Notes", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/sales-order-returns")
    public ResponseEntity<?> salesOrderReturns(@RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to,
                                               @RequestParam(required = false) Long branchId,
                                               @RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.salesReturns(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Return #", "Sale order", "Customer", "Return date", "Reason", "Qty", "Refund");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.returnNumber()), orDash(r.salesOrderNumber()), orDash(r.customerName()),
                        String.valueOf(r.returnDate()), orDash(r.reason()), String.valueOf(r.quantity()), money(r.refundTotal())));
            }
            return export("Sale Order Returns", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/customer-payments")
    public ResponseEntity<?> customerPaymentsReport(@RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to,
                                                    @RequestParam(required = false) Long branchId,
                                                    @RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.customerPayments(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Receipt #", "Customer", "Paid at", "Method", "Amount", "Note");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.receiptNumber()), orDash(r.customerName()), String.valueOf(r.paidAt()), orDash(r.method()),
                        money(r.amount()), orDash(r.note())));
            }
            return export("Customer Payments", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Snapshot, no date range — what each customer owes today (invoices and sale orders), by age. */
    @GetMapping("/receivables-aging")
    public ResponseEntity<?> receivablesAging(@RequestParam(defaultValue = "json") String format) {
        var rows = salesReportService.receivablesAging();
        if (isDownload(format)) {
            List<String> headers = List.of("Customer", "0-30 days", "31-60 days", "61-90 days", "Over 90 days", "Total outstanding");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.customerName()), money(r.days0To30()), money(r.days31To60()), money(r.days61To90()),
                        money(r.over90Days()), money(r.totalOutstanding())));
            }
            return export("Receivables Aging", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Restaurant reports (Fourier: "Add all reports related to Restaurants Including all
    // type of orders etc") — same from/to/format shape as purchase-orders/sales-orders above: no
    // registerId (a restaurant order isn't tied to a register), no categoryId (see
    // ReportService's own javadoc for why these read RestaurantOrderRepository directly instead).

    @GetMapping("/restaurant-order-type-sales")
    public ResponseEntity<?> restaurantOrderTypeSales(@RequestParam(required = false) LocalDate from,
                                                        @RequestParam(required = false) LocalDate to,
                                                        @RequestParam(required = false) Long branchId,
                                                        @RequestParam(defaultValue = "json") String format) {
        List<RestaurantOrderTypeSalesRow> rows = reportService.restaurantOrderTypeSales(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Order type", "Orders", "Qty sold", "Sales total", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantOrderTypeSalesRow r : rows) {
                body.add(List.of(r.orderType(), String.valueOf(r.orderCount()), String.valueOf(r.quantitySold()), money(r.salesTotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("Restaurant Sales By Order Type", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/restaurant-menu-item-sales")
    public ResponseEntity<?> restaurantMenuItemSales(@RequestParam(required = false) LocalDate from,
                                                       @RequestParam(required = false) LocalDate to,
                                                       @RequestParam(required = false) Long branchId,
                                                       @RequestParam(defaultValue = "json") String format) {
        List<RestaurantMenuItemSalesRow> rows = reportService.restaurantMenuItemSales(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Item", "Category", "Qty sold", "Sales total", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantMenuItemSalesRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.itemName(), r.categoryName(), String.valueOf(r.quantitySold()), money(r.salesTotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("Restaurant Menu Item Sales", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/restaurant-menu-category-sales")
    public ResponseEntity<?> restaurantMenuCategorySales(@RequestParam(required = false) LocalDate from,
                                                           @RequestParam(required = false) LocalDate to,
                                                           @RequestParam(required = false) Long branchId,
                                                           @RequestParam(defaultValue = "json") String format) {
        List<RestaurantMenuCategorySalesRow> rows = reportService.restaurantMenuCategorySales(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Qty sold", "Sales total", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantMenuCategorySalesRow r : rows) {
                body.add(List.of(r.categoryName(), String.valueOf(r.quantitySold()), money(r.salesTotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("Restaurant Menu Category Sales", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/restaurant-menu-item-profit")
    public ResponseEntity<?> restaurantMenuItemProfit(@RequestParam(required = false) LocalDate from,
                                                        @RequestParam(required = false) LocalDate to,
                                                        @RequestParam(required = false) Long branchId,
                                                        @RequestParam(defaultValue = "json") String format) {
        List<RestaurantMenuItemProfitRow> rows = reportService.restaurantMenuItemProfit(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("SKU", "Item", "Category", "Qty sold", "Revenue", "Cost", "Profit", "Margin %");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantMenuItemProfitRow r : rows) {
                body.add(List.of(orDash(r.sku()), r.itemName(), r.categoryName(), String.valueOf(r.quantitySold()), money(r.revenue()), money(r.cost()), money(r.profit()), r.marginPercent() + "%"));
            }
            return export("Restaurant Menu Item Profit", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/restaurant-menu-category-profit")
    public ResponseEntity<?> restaurantMenuCategoryProfit(@RequestParam(required = false) LocalDate from,
                                                            @RequestParam(required = false) LocalDate to,
                                                            @RequestParam(required = false) Long branchId,
                                                            @RequestParam(defaultValue = "json") String format) {
        List<RestaurantMenuCategoryProfitRow> rows = reportService.restaurantMenuCategoryProfit(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Category", "Qty sold", "Revenue", "Cost", "Profit", "Margin %");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantMenuCategoryProfitRow r : rows) {
                body.add(List.of(r.categoryName(), String.valueOf(r.quantitySold()), money(r.revenue()), money(r.cost()), money(r.profit()), r.marginPercent() + "%"));
            }
            return export("Restaurant Menu Category Profit", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Detailed transactions — one row per checked-out order, any order type (dine-in, takeaway, or delivery). */
    @GetMapping("/restaurant-order-transactions")
    public ResponseEntity<?> restaurantOrderTransactions(@RequestParam(required = false) LocalDate from,
                                                           @RequestParam(required = false) LocalDate to,
                                                           @RequestParam(required = false) Long branchId,
                                                           @RequestParam(defaultValue = "json") String format) {
        List<RestaurantOrderTransactionRow> rows = reportService.restaurantOrderTransactions(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Ticket", "Order type", "Table / customer", "Server", "Opened at", "Closed at", "Items", "Subtotal", "Tax", "Grand total", "Payment");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantOrderTransactionRow r : rows) {
                body.add(List.of(orDash(r.ticketNumber()), r.orderType(), r.tableOrCustomer(), r.serverName(), String.valueOf(r.openedAt()), String.valueOf(r.closedAt()), String.valueOf(r.itemCount()), money(r.subtotal()), money(r.taxTotal()), money(r.grandTotal()), r.paymentMethods()));
            }
            return export("Restaurant Orders", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/restaurant-server-sales")
    public ResponseEntity<?> restaurantServerSales(@RequestParam(required = false) LocalDate from,
                                                     @RequestParam(required = false) LocalDate to,
                                                     @RequestParam(required = false) Long branchId,
                                                     @RequestParam(defaultValue = "json") String format) {
        List<RestaurantServerSalesRow> rows = reportService.restaurantServerSales(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Server", "Orders", "Qty sold", "Subtotal", "Tax", "Grand total");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantServerSalesRow r : rows) {
                body.add(List.of(r.serverName(), String.valueOf(r.orderCount()), String.valueOf(r.quantitySold()), money(r.subtotal()), money(r.taxTotal()), money(r.grandTotal())));
            }
            return export("Restaurant Sales By Server", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Every order cancelled (never checked out) in the date range — see RestaurantCancelledOrderRow's own javadoc. */
    @GetMapping("/restaurant-cancelled-orders")
    public ResponseEntity<?> restaurantCancelledOrders(@RequestParam(required = false) LocalDate from,
                                                         @RequestParam(required = false) LocalDate to,
                                                         @RequestParam(required = false) Long branchId,
                                                         @RequestParam(defaultValue = "json") String format) {
        List<RestaurantCancelledOrderRow> rows = reportService.restaurantCancelledOrders(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Order type", "Table / customer", "Server", "Opened at", "Cancelled at", "Items", "Would-be total");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantCancelledOrderRow r : rows) {
                body.add(List.of(r.orderType(), r.tableOrCustomer(), r.serverName(), String.valueOf(r.openedAt()), String.valueOf(r.cancelledAt()), String.valueOf(r.itemCount()), money(r.wouldBeTotal())));
            }
            return export("Restaurant Cancelled Orders", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** "Restaurant Wastage" (Fourier: "Restaurants need a dedicated wastage module", then "should be confirmed by admin") — every recorded loss in the date range, any reason, any status. */
    @GetMapping("/restaurant-wastage")
    public ResponseEntity<?> restaurantWastage(@RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(required = false) Long branchId,
                                                @RequestParam(defaultValue = "json") String format) {
        List<RestaurantWastageReportRow> rows = reportService.restaurantWastage(start(from), end(to), branchId);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "SKU", "Product", "Qty wasted", "Reason", "Status", "Note", "Before", "After", "Cost impact");
            List<List<String>> body = new ArrayList<>();
            for (RestaurantWastageReportRow r : rows) {
                body.add(List.of(String.valueOf(r.recordedAt()), orDash(r.sku()), r.productName(), String.valueOf(r.quantity()), r.reason(), orDash(r.status()), orDash(r.note()),
                        r.stockBefore() == null ? "—" : String.valueOf(r.stockBefore()), r.stockAfter() == null ? "—" : String.valueOf(r.stockAfter()), money(r.costImpact())));
            }
            return export("Restaurant Wastage", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- helpers ----

    /** Defaults to "the last 30 days" if no range is given, so hitting an endpoint directly still returns something sensible. */
    private Instant start(LocalDate from) {
        LocalDate effective = from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        return effective.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant end(LocalDate to) {
        LocalDate effective = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        return effective.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    }

    private boolean isDownload(String format) {
        return "xlsx".equalsIgnoreCase(format) || "pdf".equalsIgnoreCase(format);
    }

    private ResponseEntity<byte[]> export(String title, List<String> headers, List<List<String>> rows, String format) {
        byte[] content = "xlsx".equalsIgnoreCase(format)
                ? exportService.toXlsx(title, headers, rows)
                : exportService.toPdf(title, headers, rows);
        String filename = title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ("xlsx".equalsIgnoreCase(format) ? ".xlsx" : ".pdf");
        MediaType mediaType = "xlsx".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }

    private static String money(java.math.BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    // ---- Work order reports — see WorkOrderReportService. ----

    @GetMapping("/work-orders")
    public ResponseEntity<?> workOrders(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                        @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = workOrderReportService.workOrders(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Number", "Type", "Status", "Priority", "Title", "Customer", "Asset", "Assigned to", "Requested", "Due", "Hours",
                    "Parts cost", "External cost", "Invoice");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.number(), r.type(), r.status(), r.priority(), r.title(), orDash(r.customer()), orDash(r.asset()), orDash(r.assignedTo()),
                        String.valueOf(r.requested()), r.due() == null ? "—" : r.due().toString(), dec(r.hours()), money(r.partsCost()), money(r.externalCost()),
                        orDash(r.invoice())));
            }
            return export("Work Orders", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/work-order-profit")
    public ResponseEntity<?> workOrderProfit(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                             @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = workOrderReportService.profitability(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Number", "Title", "Customer", "Completed", "Invoiced", "Parts cost", "External cost", "Hours", "Margin", "Margin %");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.number(), r.title(), orDash(r.customer()), String.valueOf(r.completed()), money(r.revenue()), money(r.partsCost()),
                        money(r.externalCost()), dec(r.hours()), money(r.margin()), dec(r.marginPercent())));
            }
            return export("Work Order Profitability", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/work-order-assets")
    public ResponseEntity<?> workOrderAssets(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                             @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = workOrderReportService.assetCosts(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Asset", "Jobs", "Hours", "Parts cost", "External cost", "Total cost", "Last job");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(orDash(r.asset()), String.valueOf(r.jobs()), dec(r.hours()), money(r.partsCost()), money(r.externalCost()), money(r.totalCost()),
                        r.lastJob() == null ? "—" : r.lastJob().toString()));
            }
            return export("Maintenance Cost by Asset", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/work-order-overdue")
    public ResponseEntity<?> workOrderOverdue(@RequestParam(defaultValue = "json") String format) {
        var rows = workOrderReportService.overdue();
        if (isDownload(format)) {
            List<String> headers = List.of("Number", "Title", "Customer", "Assigned to", "Priority", "Status", "Due", "Days overdue");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.number(), r.title(), orDash(r.customer()), orDash(r.assignedTo()), r.priority(), r.status(), String.valueOf(r.due()),
                        String.valueOf(r.daysOverdue())));
            }
            return export("Overdue Work Orders", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Booking reports — see BookingReportService. ----

    @GetMapping("/booking-appointments")
    public ResponseEntity<?> bookingAppointments(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = bookingReportService.appointments(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Time", "Number", "Service", "Customer", "Staff", "Room / equipment", "Status", "Price", "Paid", "Invoice");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.date()), r.time(), r.number(), r.service(), r.customer(), orDash(r.staff()), orDash(r.resource()), r.status(),
                        money(r.price()), money(r.paid()), orDash(r.invoice())));
            }
            return export("Appointments", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/booking-utilisation")
    public ResponseEntity<?> bookingUtilisation(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                                @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = bookingReportService.utilisation(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Type", "Name", "Appointments", "Booked hours", "Available hours", "Utilisation %");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.type(), r.name(), String.valueOf(r.appointments()), dec(r.bookedHours()), dec(r.availableHours()), dec(r.utilisationPercent())));
            }
            return export("Booking Utilisation", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/booking-services")
    public ResponseEntity<?> bookingServices(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                             @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = bookingReportService.services(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Service", "Booked", "Completed", "Cancelled", "No-shows", "No-show %", "Value");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.service(), String.valueOf(r.booked()), String.valueOf(r.completed()), String.valueOf(r.cancelled()), String.valueOf(r.noShows()),
                        dec(r.noShowPercent()), money(r.value())));
            }
            return export("Bookings by Service", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- Law firm reports — see LawReportService. ----

    @GetMapping("/law-matters")
    public ResponseEntity<?> lawMatters(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                        @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfYear(1);
        var rows = lawReportService.matters(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Number", "Title", "Client", "Practice area", "Status", "Lead", "Opened", "Closed", "Billing", "Open tasks", "Next deadline");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.number(), r.title(), orDash(r.client()), orDash(r.practiceArea()), r.status(), orDash(r.lead()), String.valueOf(r.opened()),
                        r.closed() != null ? r.closed().toString() : "-", r.billing(), String.valueOf(r.openTasks()), r.nextDeadline() != null ? r.nextDeadline().toString() : "-"));
            }
            return export("Law Matters", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-deadlines")
    public ResponseEntity<?> lawDeadlines(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                          @RequestParam(defaultValue = "json") String format) {
        LocalDate start = from != null ? from : LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to != null ? to : start.plusDays(30);
        var rows = lawReportService.deadlines(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Due", "Time", "Type", "Status", "Deadline", "Matter", "Client", "Assigned to", "Overdue");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.due()), orDash(r.time()), r.type(), r.status(), r.title(), r.matter(), orDash(r.client()), orDash(r.assignedTo()), r.overdue()));
            }
            return export("Law Deadlines", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-billing")
    public ResponseEntity<?> lawBilling(@RequestParam(defaultValue = "true") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        var rows = lawReportService.billing(openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Matter", "Title", "Client", "Billing", "Status", "Hours", "Unbilled time", "Unbilled costs", "Fixed-fee stages to bill", "Billed", "Paid", "Outstanding", "Retainer left");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.number(), r.title(), orDash(r.client()), r.billing(), r.status(), String.valueOf(r.hours()), String.valueOf(r.unbilledTime()),
                        String.valueOf(r.unbilledCosts()), String.valueOf(r.stagesPlanned()), String.valueOf(r.billed()), String.valueOf(r.paid()), String.valueOf(r.outstanding()),
                        String.valueOf(r.retainerBalance())));
            }
            return export("Law Billing", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-fees")
    public ResponseEntity<?> lawFees(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                     @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = lawReportService.fees(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Matter", "Title", "Client", "Kind", "Invoice", "Invoice status", "Fees", "Costs", "Retainer applied", "Billed", "Paid", "Outstanding");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.date()), r.matter(), r.title(), orDash(r.client()), r.kind(), orDash(r.invoice()), orDash(r.invoiceStatus()),
                        String.valueOf(r.fees()), String.valueOf(r.costs()), String.valueOf(r.retainerApplied()), String.valueOf(r.amount()), String.valueOf(r.paid()),
                        String.valueOf(r.outstanding())));
            }
            return export("Law Fees Billed", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-trust-balances")
    public ResponseEntity<?> lawTrustBalances(@RequestParam(defaultValue = "true") boolean heldOnly, @RequestParam(defaultValue = "json") String format) {
        var rows = lawReportService.trustBalances(heldOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Matter", "Title", "Client", "Status", "Held", "Waiting to be paid out", "Last activity");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.matter(), r.title(), orDash(r.client()), r.status(), String.valueOf(r.balance()), String.valueOf(r.pending()), String.valueOf(r.lastActivity())));
            }
            return export("Client Money Held", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-trust-ledger")
    public ResponseEntity<?> lawTrustLedger(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                            @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = lawReportService.trustLedger(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Date", "Number", "Matter", "Client", "Type", "Party", "Reference", "Money in", "Money out", "Balance");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(String.valueOf(r.date()), r.number(), r.matter(), orDash(r.client()), r.type(), orDash(r.party()), orDash(r.reference()),
                        r.moneyIn() == null ? "" : String.valueOf(r.moneyIn()), r.moneyOut() == null ? "" : String.valueOf(r.moneyOut()), String.valueOf(r.balance())));
            }
            return export("Client Money Ledger", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-workload")
    public ResponseEntity<?> lawWorkload(@RequestParam(defaultValue = "json") String format) {
        var rows = lawReportService.workload();
        if (isDownload(format)) {
            List<String> headers = List.of("Lawyer", "Matters led", "Matters on", "Open tasks", "Overdue tasks", "Deadlines (next 30 days)", "Overdue deadlines");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.lawyer(), String.valueOf(r.mattersLed()), String.valueOf(r.mattersOn()), String.valueOf(r.openTasks()), String.valueOf(r.overdueTasks()),
                        String.valueOf(r.deadlinesNext30()), String.valueOf(r.overdueDeadlines())));
            }
            return export("Law Workload", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }
}
