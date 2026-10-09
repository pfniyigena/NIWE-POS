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
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService exportService;
    private final com.posapp.tracking.TrackingService trackingService;

    public ReportController(ReportService reportService, ReportExportService exportService, com.posapp.tracking.TrackingService trackingService) {
        this.reportService = reportService;
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
}
