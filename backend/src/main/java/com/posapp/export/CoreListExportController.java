package com.posapp.export;

import com.posapp.bill.BillController;
import com.posapp.bill.dto.BillDto;
import com.posapp.branch.BranchService;
import com.posapp.branch.dto.BranchDto;
import com.posapp.collections.CollectionDtos.CustomerRow;
import com.posapp.collections.CollectionDtos.ItemRow;
import com.posapp.collections.CollectionService;
import com.posapp.common.ExportFiles;
import com.posapp.credit.CustomerPaymentController;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.customer.CustomerController;
import com.posapp.customer.dto.CustomerDto;
import com.posapp.inventory.PurchaseOrderController;
import com.posapp.inventory.SupplierController;
import com.posapp.inventory.SupplierPaymentListController;
import com.posapp.inventory.dto.PurchaseOrderDto;
import com.posapp.inventory.dto.SupplierDto;
import com.posapp.inventory.dto.SupplierPaymentDto;
import com.posapp.invoice.InvoiceController;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.quotation.QuotationController;
import com.posapp.quotation.dto.QuotationDto;
import com.posapp.reports.ReportExportService;
import com.posapp.salesorder.SalesOrderController;
import com.posapp.salesorder.dto.SalesOrderDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /api/export/<list>}: the core lists (invoices, quotations, orders, bills, payments, customers,
 * suppliers) as Excel (default), PDF or CSV. Each export takes the same filters as its screen and returns
 * the whole filtered list, not one page.
 *
 * <p>The rows come from the list endpoints themselves (called through their Spring proxies), so the filters,
 * the branch rule and the access rule of each screen apply here unchanged, and the file always matches the screen.
 */
@RestController
@RequestMapping("/api/export")
@PreAuthorize("isAuthenticated()")
public class CoreListExportController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InvoiceController invoices;
    private final QuotationController quotations;
    private final SalesOrderController salesOrders;
    private final PurchaseOrderController purchaseOrders;
    private final BillController bills;
    private final CustomerPaymentController customerPayments;
    private final SupplierPaymentListController supplierPayments;
    private final CustomerController customers;
    private final SupplierController suppliers;
    private final BranchService branchService;
    private final ReportExportService exportService;
    private final CollectionService collections;

    public CoreListExportController(InvoiceController invoices, QuotationController quotations, SalesOrderController salesOrders, PurchaseOrderController purchaseOrders,
                                    BillController bills, CustomerPaymentController customerPayments, SupplierPaymentListController supplierPayments,
                                    CustomerController customers, SupplierController suppliers, BranchService branchService, ReportExportService exportService,
                                    CollectionService collections) {
        this.invoices = invoices;
        this.quotations = quotations;
        this.salesOrders = salesOrders;
        this.purchaseOrders = purchaseOrders;
        this.bills = bills;
        this.customerPayments = customerPayments;
        this.supplierPayments = supplierPayments;
        this.customers = customers;
        this.suppliers = suppliers;
        this.branchService = branchService;
        this.exportService = exportService;
        this.collections = collections;
    }

    // ---- small formatting helpers

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String day(Instant t) {
        return t == null ? "" : DAY.format(t.atZone(ZoneId.systemDefault()));
    }

    private static String stamp(Instant t) {
        return t == null ? "" : STAMP.format(t.atZone(ZoneId.systemDefault()));
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String yes(boolean b) {
        return b ? "Yes" : "No";
    }

    private static <T> List<T> all(ResponseEntity<List<T>> r) {
        return r.getBody() == null ? List.of() : r.getBody();
    }

    private Map<Long, String> branchNames() {
        Map<Long, String> m = new HashMap<>();
        for (BranchDto b : branchService.listAll()) m.put(b.id(), b.name());
        return m;
    }

    private String branchOf(Long id, Map<Long, String> names, Long hq) {
        Long eff = id != null ? id : hq;
        return eff == null ? "" : names.getOrDefault(eff, "");
    }

    /** "Status ISSUED · 2026-10-01 to 2026-10-20 · search “abc”" — printed in the title so a file says which filters made it. */
    private static String title(String name, String q, String status, LocalDate from, LocalDate to, String branch) {
        List<String> bits = new ArrayList<>();
        if (status != null && !status.isBlank()) bits.add("status " + status);
        if (from != null || to != null) bits.add((from == null ? "…" : from.toString()) + " to " + (to == null ? "…" : to.toString()));
        if (branch != null && !branch.isBlank()) bits.add(branch);
        if (q != null && !q.isBlank()) bits.add("search \"" + q.trim() + "\"");
        return bits.isEmpty() ? name : name + " (" + String.join(", ", bits) + ")";
    }

    private ResponseEntity<byte[]> out(String title, String base, List<String> headers, List<List<String>> body, String format) {
        return ExportFiles.file(exportService, title, base, headers, body, format);
    }

    private String branchLabel(Long branchId, Map<Long, String> names) {
        return branchId == null ? null : names.getOrDefault(branchId, "branch " + branchId);
    }

    // ---- sales side

    @GetMapping("/invoices")
    public ResponseEntity<byte[]> invoices(@RequestParam(required = false) Long customerId, @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long branchId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<InvoiceDto> rows = all(invoices.listAll(customerId, q, status, branchId, from, to, null, null));
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Invoice #", "Sale order", "Customer", "Date", "Due date", "Status", "Branch", "Total", "Paid", "Outstanding");
        List<List<String>> body = new ArrayList<>();
        for (InvoiceDto r : rows) {
            body.add(List.of(s(r.invoiceNumber()), s(r.salesOrderNumber()), s(r.customerName()), day(r.invoiceDate()), s(r.dueDate()), s(r.status()), branchOf(r.branchId(), names, hq),
                    money(r.totalAmount()), money(r.amountPaid()), "ISSUED".equals(r.status()) ? money(r.amountOutstanding()) : ""));
        }
        return out(title("Invoices", q, status, from, to, branchLabel(branchId, names)), "invoices", h, body, format);
    }

    @GetMapping("/credit-notes")
    public ResponseEntity<byte[]> creditNotes(@RequestParam(required = false) String q, @RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<InvoiceDto> rows = all(invoices.listCreditNotes(q, branchId, from, to, null, null));
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Credit note #", "Invoice #", "Customer", "Voided on", "Reason", "Branch", "Amount", "Refund method");
        List<List<String>> body = new ArrayList<>();
        for (InvoiceDto r : rows) {
            body.add(List.of(s(r.creditNoteNumber()), s(r.invoiceNumber()), s(r.customerName()), stamp(r.voidedAt()), s(r.voidReason()), branchOf(r.branchId(), names, hq), money(r.totalAmount()),
                    s(r.refundMethod())));
        }
        return out(title("Credit notes", q, null, from, to, branchLabel(branchId, names)), "credit-notes", h, body, format);
    }

    @GetMapping("/quotations")
    public ResponseEntity<byte[]> quotations(@RequestParam(required = false) Long customerId, @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long branchId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<QuotationDto> rows = all(quotations.listAll(customerId, q, status, branchId, from, to, null, null));
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Quote #", "Customer", "Date", "Valid until", "Status", "Branch", "Total", "Sale order");
        List<List<String>> body = new ArrayList<>();
        for (QuotationDto r : rows) {
            body.add(List.of(s(r.quoteNumber()), s(r.customerName()), day(r.quoteDate()), s(r.validUntil()), s(r.status()), branchOf(r.branchId(), names, hq), money(r.totalAmount()),
                    s(r.convertedSalesOrderNumber())));
        }
        return out(title("Quotations", q, status, from, to, branchLabel(branchId, names)), "quotations", h, body, format);
    }

    @GetMapping("/sales-orders")
    public ResponseEntity<byte[]> salesOrders(@RequestParam(required = false) Long customerId, @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long branchId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<SalesOrderDto> rows = all(salesOrders.listAll(customerId, q, status, branchId, from, to, null, null));
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Order #", "Customer", "Date", "Due date", "Status", "Branch", "Total", "Paid", "Outstanding");
        List<List<String>> body = new ArrayList<>();
        for (SalesOrderDto r : rows) {
            body.add(List.of(s(r.orderNumber()), s(r.customerName()), day(r.orderDate()), s(r.dueDate()), s(r.status()), branchOf(r.branchId(), names, hq), money(r.totalAmount()),
                    money(r.amountPaid()), money(r.amountOutstanding())));
        }
        return out(title("Sale orders", q, status, from, to, branchLabel(branchId, names)), "sale-orders", h, body, format);
    }

    @GetMapping("/customer-payments")
    public ResponseEntity<byte[]> customerPayments(@RequestParam(required = false) String q, @RequestParam(required = false) String method, @RequestParam(required = false) Long customerId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<CustomerPaymentDto> rows = all(customerPayments.allPayments(q, method, customerId, from, to, null, null));
        List<String> h = List.of("Receipt #", "Customer", "Date", "Amount", "Method", "Recorded by", "Note", "Applied to");
        List<List<String>> body = new ArrayList<>();
        for (CustomerPaymentDto r : rows) {
            String applied = r.allocations() == null ? "" : r.allocations().stream().map(a -> s(a.invoiceNumber()) + " " + money(a.amountApplied())).collect(Collectors.joining("; "));
            body.add(List.of(s(r.receiptNumber()), s(r.customerName()), stamp(r.paidAt()), money(r.amount()), s(r.method()), s(r.recordedByName()), s(r.note()), applied));
        }
        return out(title("Customer payments", q, method, from, to, null), "customer-payments", h, body, format);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_BILLING_VIEW')")
    @GetMapping("/customers")
    public ResponseEntity<byte[]> customers(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean credit, @RequestParam(defaultValue = "xlsx") String format) {
        List<CustomerDto> rows = all(customers.list(q, credit, null, null));
        List<String> h = List.of("Name", "Phone", "E-mail", "Tax ID", "Credit allowed", "Credit limit", "Payment terms (days)");
        List<List<String>> body = new ArrayList<>();
        for (CustomerDto r : rows) {
            body.add(List.of(s(r.name()), s(r.phone()), s(r.email()), s(r.taxId()), yes(r.creditEnabled()), money(r.creditLimit()), s(r.defaultCreditTermDays())));
        }
        return out(title("Customers", q, credit == null ? null : (credit ? "credit allowed" : "no credit"), null, null, null), "customers", h, body, format);
    }

    // ---- purchase side

    @GetMapping("/purchase-orders")
    public ResponseEntity<byte[]> purchaseOrders(@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Long branchId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<PurchaseOrderDto> rows = all(purchaseOrders.listAll(supplierId, q, status, branchId, from, to, null, null));
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Order #", "Supplier", "Date", "Expected", "Status", "Branch", "Bill #", "Total", "Paid", "Outstanding");
        List<List<String>> body = new ArrayList<>();
        for (PurchaseOrderDto r : rows) {
            body.add(List.of(s(r.orderNumber()), s(r.supplierName()), day(r.orderDate()), day(r.expectedDate()), s(r.status()), branchOf(r.branchId(), names, hq), s(r.billNumber()),
                    money(r.totalAmount()), money(r.amountPaid()), money(r.amountOutstanding())));
        }
        return out(title("Purchase orders", q, status, from, to, branchLabel(branchId, names)), "purchase-orders", h, body, format);
    }

    @GetMapping("/bills")
    public ResponseEntity<byte[]> bills(@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long branchId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<BillDto> rows = all(bills.listAll(supplierId, q, status, branchId, from, to, null, null));
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Bill #", "Purchase order", "Supplier", "Date", "Status", "Branch", "Total", "Paid", "Outstanding");
        List<List<String>> body = new ArrayList<>();
        for (BillDto r : rows) {
            body.add(List.of(s(r.billNumber()), s(r.purchaseOrderNumber()), s(r.supplierName()), day(r.billDate()), s(r.status()), branchOf(r.branchId(), names, hq), money(r.totalAmount()),
                    money(r.amountPaid()), money(r.amountOutstanding())));
        }
        return out(title("Bills", q, status, from, to, branchLabel(branchId, names)), "bills", h, body, format);
    }

    @GetMapping("/refunds")
    public ResponseEntity<byte[]> refunds(@RequestParam(required = false) String q, @RequestParam(required = false) Long branchId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<BillDto> rows = all(bills.listRefunds(q, branchId, from, to, null, null));
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Refund #", "Bill #", "Supplier", "Voided on", "Reason", "Branch", "Amount");
        List<List<String>> body = new ArrayList<>();
        for (BillDto r : rows) {
            body.add(List.of(s(r.refundNumber()), s(r.billNumber()), s(r.supplierName()), stamp(r.voidedAt()), s(r.voidReason()), branchOf(r.branchId(), names, hq), money(r.totalAmount())));
        }
        return out(title("Refunds", q, null, from, to, branchLabel(branchId, names)), "refunds", h, body, format);
    }

    @GetMapping("/supplier-payments")
    public ResponseEntity<byte[]> supplierPayments(@RequestParam(required = false) String q, @RequestParam(required = false) String method, @RequestParam(required = false) Long supplierId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        List<SupplierPaymentDto> rows = all(supplierPayments.allPayments(q, method, supplierId, from, to, null, null));
        List<String> h = List.of("Receipt #", "Supplier", "Date", "Amount", "Method", "Recorded by", "Note", "Applied to");
        List<List<String>> body = new ArrayList<>();
        for (SupplierPaymentDto r : rows) {
            String applied = r.allocations() == null ? "" : r.allocations().stream().map(a -> s(a.orderNumber()) + " " + money(a.amountApplied())).collect(Collectors.joining("; "));
            body.add(List.of(s(r.receiptNumber()), s(r.supplierName()), stamp(r.paidAt()), money(r.amount()), s(r.method()), s(r.recordedByName()), s(r.note()), applied));
        }
        return out(title("Supplier payments", q, method, from, to, null), "supplier-payments", h, body, format);
    }

    @GetMapping("/suppliers")
    public ResponseEntity<byte[]> suppliers(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "xlsx") String format) {
        List<SupplierDto> rows = all(suppliers.listAll(q, active, null, null));
        List<String> h = List.of("Name", "Contact", "Phone", "E-mail", "Tax ID", "Address", "Active");
        List<List<String>> body = new ArrayList<>();
        for (SupplierDto r : rows) {
            body.add(List.of(s(r.name()), s(r.contactName()), s(r.phone()), s(r.email()), s(r.taxId()), s(r.address()), yes(r.active())));
        }
        return out(title("Suppliers", q, active == null ? null : (active ? "active" : "inactive"), null, null, null), "suppliers", h, body, format);
    }

    // ---- collections

    private static final String COLLECTIONS_VIEW = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW') or hasAuthority('AREA_SALES_VIEW')";

    @PreAuthorize(COLLECTIONS_VIEW)
    @GetMapping("/collections")
    public ResponseEntity<byte[]> collectionItems(@RequestParam(defaultValue = "true") boolean overdueOnly, @RequestParam(required = false) String bucket, @RequestParam(required = false) Long customerId,
                                                  @RequestParam(required = false) Long branchId, @RequestParam(required = false) String q, @RequestParam(defaultValue = "xlsx") String format) {
        List<ItemRow> rows = collections.allItems(overdueOnly, bucket, customerId, branchId, q);
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<String> h = List.of("Customer", "Number", "What", "Date", "Due date", "Days overdue", "Age", "Branch", "Total", "Paid", "Still to pay", "E-mail", "Phone");
        List<List<String>> body = new ArrayList<>();
        for (ItemRow r : rows) {
            body.add(List.of(s(r.customerName()), s(r.number()), "POS_SALE".equals(r.kind()) ? "Sale on credit" : "Invoice", s(r.date()), s(r.dueDate()) + (r.dueAssumed() ? " (from term)" : ""),
                    s(r.daysOverdue()), "CURRENT".equals(r.bucket()) ? "Not yet due" : r.bucket() + " days", branchOf(r.branchId(), names, hq), money(r.total()), money(r.paid()), money(r.outstanding()),
                    s(r.customerEmail()), s(r.customerPhone())));
        }
        return out(title(overdueOnly ? "Overdue payments" : "Unpaid items", q, bucket, null, null, branchLabel(branchId, names)), "overdue-payments", h, body, format);
    }

    @PreAuthorize(COLLECTIONS_VIEW)
    @GetMapping("/collection-customers")
    public ResponseEntity<byte[]> collectionCustomers(@RequestParam(defaultValue = "true") boolean overdueOnly, @RequestParam(required = false) Long branchId, @RequestParam(required = false) String q,
                                                      @RequestParam(defaultValue = "xlsx") String format) {
        List<CustomerRow> rows = collections.allCustomers(overdueOnly, branchId, q);
        Map<Long, String> names = branchNames();
        List<String> h = List.of("Customer", "E-mail", "Phone", "Unpaid items", "Overdue items", "Total owed", "Overdue", "Oldest (days late)", "Not yet due", "1-30 days", "31-60 days", "61-90 days", "Over 90 days",
                "Reminders sent", "Last reminder");
        List<List<String>> body = new ArrayList<>();
        for (CustomerRow r : rows) {
            body.add(List.of(s(r.name()), s(r.email()), s(r.phone()), s(r.items()), s(r.overdueItems()), money(r.outstanding()), money(r.overdue()), s(r.oldestDaysOverdue()), money(r.aging().current()),
                    money(r.aging().days1to30()), money(r.aging().days31to60()), money(r.aging().days61to90()), money(r.aging().over90()), s(r.remindersSent()), stamp(r.lastRemindedAt())));
        }
        return out(title(overdueOnly ? "Customers with overdue payments" : "Customers who owe", q, null, null, null, branchLabel(branchId, names)), "customers-who-owe", h, body, format);
    }
}
