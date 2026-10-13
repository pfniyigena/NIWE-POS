package com.posapp.reportschedule;

import com.posapp.reports.ReportController;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The reports that can be scheduled. Each entry calls the same {@link ReportController} method the report
 * screen's Excel/PDF buttons call, so a scheduled file always matches what the screen shows. Add a new
 * report here to make it schedulable.
 */
public final class ScheduledReportCatalog {

    private ScheduledReportCatalog() {
    }

    /** Everything an invoker may need; unused fields are simply ignored by a given report. */
    public record Params(LocalDate from, LocalDate to, Long categoryId, Long warehouseId, Long branchId, Integer days, String format) {
    }

    @FunctionalInterface
    public interface Invoker {
        ResponseEntity<?> call(ReportController c, Params p);
    }

    /**
     * @param periodic    true = covers the period that just ended (from/to are filled in)
     * @param categoryFilter / warehouseFilter / branchFilter / daysFilter which optional filters apply
     */
    public record Def(String key, String label, String section, boolean periodic, boolean categoryFilter,
                      boolean warehouseFilter, boolean branchFilter, boolean daysFilter, Integer defaultDays, Invoker invoker) {
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void add(Def d) {
        DEFS.put(d.key(), d);
    }

    static {
        // key, label, section, periodic, category, warehouse, branch, days, defaultDays, invoker
        add(new Def("reorder-list", "Reorder list (products at or below reorder level)", "Inventory", false, false, false, false, false, null, null));
        add(new Def("stock-valuation", "Stock on hand and valuation", "Inventory", false, true, true, true, false, null,
                (c, p) -> c.stockValuation(p.categoryId(), p.warehouseId(), p.warehouseId() == null, p.branchId(), p.format())));
        add(new Def("stock-movement", "Stock movement history", "Inventory", true, true, true, true, false, null,
                (c, p) -> c.stockMovement(p.from(), p.to(), p.categoryId(), null, p.warehouseId(), p.branchId(), p.format())));
        add(new Def("stock-aging", "Stock aging", "Inventory", false, true, true, true, false, null,
                (c, p) -> c.stockAging(p.categoryId(), p.warehouseId(), p.branchId(), p.format())));
        add(new Def("dead-stock", "Dead stock", "Inventory", false, true, true, true, true, 90,
                (c, p) -> c.deadStock(p.categoryId(), p.warehouseId(), p.branchId(), p.days() != null ? p.days() : 90, p.format())));
        add(new Def("expiring-stock", "Expiring stock", "Inventory", false, true, true, false, true, 60,
                (c, p) -> c.expiringStock(p.categoryId(), p.warehouseId(), p.days() != null ? p.days() : 60, p.format())));
        add(new Def("batch-stock", "Batch stock (lots and expiry)", "Inventory", false, true, true, false, false, null,
                (c, p) -> c.batchStock(p.categoryId(), p.warehouseId(), p.format())));
        add(new Def("purchase-orders", "Purchase orders", "Purchasing", true, false, false, true, false, null,
                (c, p) -> c.purchaseOrders(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("outstanding-payables", "Outstanding payables", "Purchasing", false, false, false, false, false, null,
                (c, p) -> c.outstandingPayables(p.format())));
        add(new Def("purchases-by-supplier", "Purchases by supplier", "Purchasing", true, false, false, true, false, null,
                (c, p) -> c.purchasesBySupplier(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("purchases-by-product", "Purchases by product", "Purchasing", true, false, false, true, false, null,
                (c, p) -> c.purchasesByProduct(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("purchases-by-category", "Purchases by category", "Purchasing", true, false, false, true, false, null,
                (c, p) -> c.purchasesByCategory(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("purchase-bills", "Bills", "Purchasing", true, false, false, true, false, null,
                (c, p) -> c.purchaseBills(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("purchase-returns", "Purchase returns", "Purchasing", true, false, false, true, false, null,
                (c, p) -> c.purchaseReturns(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("supplier-payments", "Supplier payments", "Purchasing", true, false, false, true, false, null,
                (c, p) -> c.supplierPayments(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("payables-aging", "Payables aging", "Purchasing", false, false, false, false, false, null,
                (c, p) -> c.payablesAging(p.format())));
        add(new Def("sales-orders", "Sales orders", "Sales", true, false, false, true, false, null,
                (c, p) -> c.salesOrders(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("sales-by-customer", "Sales by customer", "Sales", true, false, false, true, false, null,
                (c, p) -> c.salesByCustomer(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("sales-by-product", "Sales by product (sale orders)", "Sales", true, false, false, true, false, null,
                (c, p) -> c.salesByProduct(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("sales-by-category", "Sales by category (sale orders)", "Sales", true, false, false, true, false, null,
                (c, p) -> c.salesByCategory(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("sales-invoices", "Invoices", "Sales", true, false, false, true, false, null,
                (c, p) -> c.salesInvoices(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("sales-credit-notes", "Credit notes", "Sales", true, false, false, true, false, null,
                (c, p) -> c.salesCreditNotes(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("sales-order-returns", "Sale order returns", "Sales", true, false, false, true, false, null,
                (c, p) -> c.salesOrderReturns(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("customer-payments", "Customer payments", "Sales", true, false, false, true, false, null,
                (c, p) -> c.customerPaymentsReport(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("receivables-aging", "Receivables aging", "Sales", false, false, false, false, false, null,
                (c, p) -> c.receivablesAging(p.format())));
        add(new Def("product-sales", "Total POS products sales", "Sales", true, true, false, true, false, null,
                (c, p) -> c.productSales(p.from(), p.to(), null, p.branchId(), p.categoryId(), p.format())));
        add(new Def("category-sales", "Total POS categories sales", "Sales", true, false, false, true, false, null,
                (c, p) -> c.categorySales(p.from(), p.to(), null, p.branchId(), p.format())));
        add(new Def("credit-sales", "Credit sales", "Credit", true, false, false, true, false, null,
                (c, p) -> c.creditSales(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("outstanding-credit", "Outstanding credit", "Credit", false, false, false, true, false, null,
                (c, p) -> c.outstandingCredit(p.branchId(), p.format())));
        add(new Def("overdue-credit", "Overdue credit", "Credit", false, false, false, true, false, null,
                (c, p) -> c.overdueCredit(p.branchId(), p.format())));
        add(new Def("asset-register", "Asset register", "Assets", false, false, false, true, false, null,
                (c, p) -> c.assetRegister(p.branchId(), p.format())));
        add(new Def("assets-by-category", "Assets by category", "Assets", false, false, false, true, false, null,
                (c, p) -> c.assetsByCategory(p.branchId(), p.format())));
        add(new Def("depreciation-schedule", "Depreciation schedule", "Assets", true, false, false, true, false, null,
                (c, p) -> c.depreciationSchedule(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("asset-disposals", "Asset disposals", "Assets", true, false, false, true, false, null,
                (c, p) -> c.assetDisposals(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("asset-transfers", "Asset transfers", "Assets", true, false, false, true, false, null,
                (c, p) -> c.assetTransfers(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("installment-agreements", "Instalment agreements", "Installments", false, false, false, true, false, null,
                (c, p) -> c.installmentAgreements(p.branchId(), p.format())));
        add(new Def("installments-due", "Instalments due", "Installments", false, false, false, true, true, 30,
                (c, p) -> c.installmentsDue(p.days(), p.branchId(), p.format())));
        add(new Def("installments-overdue", "Overdue instalments", "Installments", false, false, false, true, false, null,
                (c, p) -> c.installmentsOverdue(p.branchId(), p.format())));
        add(new Def("installment-collections", "Instalment collections", "Installments", true, false, false, true, false, null,
                (c, p) -> c.installmentCollections(p.from(), p.to(), p.branchId(), p.format())));
        add(new Def("employee-directory", "Employee directory", "HR", false, false, false, false, false, null,
                (c, p) -> c.employeeDirectory(p.format())));
        add(new Def("headcount-by-department", "Headcount by department", "HR", false, false, false, false, false, null,
                (c, p) -> c.headcountByDepartment(p.format())));
        add(new Def("leave-balances", "Leave balances", "HR", true, false, false, false, false, null,
                (c, p) -> c.leaveBalances(p.to(), p.format())));
        add(new Def("leave-requests", "Leave requests", "HR", true, false, false, false, false, null,
                (c, p) -> c.leaveRequestsReport(p.from(), p.to(), p.format())));
        add(new Def("attendance-summary", "Attendance summary", "HR", true, false, false, false, false, null,
                (c, p) -> c.attendanceSummary(p.from(), p.to(), p.format())));
        add(new Def("contracts-expiring", "Contracts expiring", "HR", false, false, false, false, true, 60,
                (c, p) -> c.contractsExpiring(p.days(), p.format())));
        add(new Def("payroll-register", "Payroll register", "HR", true, false, false, false, false, null,
                (c, p) -> c.payrollRegister(p.from(), p.to(), p.format())));
        add(new Def("payroll-summary", "Payroll summary", "HR", true, false, false, false, false, null,
                (c, p) -> c.payrollSummary(p.from(), p.to(), p.format())));
    }

    public static List<Def> all() {
        return List.copyOf(DEFS.values());
    }

    public static Def find(String key) {
        return key == null ? null : DEFS.get(key);
    }
}
