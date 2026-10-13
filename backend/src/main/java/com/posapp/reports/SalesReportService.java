package com.posapp.reports;

import com.posapp.branch.BranchService;
import com.posapp.catalog.Category;
import com.posapp.catalog.Product;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.CustomerPayment;
import com.posapp.credit.CustomerPaymentRepository;
import com.posapp.customer.Customer;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.reports.dto.SalesReportRows.*;
import com.posapp.salesorder.SalesOrder;
import com.posapp.salesorder.SalesOrderLine;
import com.posapp.salesorder.SalesOrderRepository;
import com.posapp.salesorder.SalesOrderReturn;
import com.posapp.salesorder.SalesOrderReturnLine;
import com.posapp.salesorder.SalesOrderReturnRepository;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.SalesOrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Sales reports built on sale orders, invoices, credit notes, returns and customer payments (Sales > Reports) — the
 * selling mirror of {@link PurchaseReportService}. "Sales" here means confirmed sale orders (confirmed, partly or fully
 * fulfilled); drafts and cancelled orders are not sales. Amounts are the same tax-inclusive figures the Sale Order screens
 * show. The POS sales reports are separate (ReportService).
 */
@Service
public class SalesReportService {

    private static final List<SalesOrderStatus> CONFIRMED =
            List.of(SalesOrderStatus.CONFIRMED, SalesOrderStatus.PARTIALLY_FULFILLED, SalesOrderStatus.FULFILLED);

    private final SalesOrderRepository salesOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final SalesOrderReturnRepository salesOrderReturnRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final CustomerCreditService customerCreditService;
    private final BranchService branchService;

    public SalesReportService(SalesOrderRepository salesOrderRepository, InvoiceRepository invoiceRepository,
                              SalesOrderReturnRepository salesOrderReturnRepository,
                              CustomerPaymentRepository customerPaymentRepository,
                              CustomerCreditService customerCreditService, BranchService branchService) {
        this.salesOrderRepository = salesOrderRepository;
        this.invoiceRepository = invoiceRepository;
        this.salesOrderReturnRepository = salesOrderReturnRepository;
        this.customerPaymentRepository = customerPaymentRepository;
        this.customerCreditService = customerCreditService;
        this.branchService = branchService;
    }

    private List<SalesOrder> confirmedOrders(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<SalesOrder> result = new ArrayList<>();
        for (SalesOrder so : salesOrderRepository.findByOrderDateBetweenOrderByOrderDateDesc(from, to)) {
            if (!CONFIRMED.contains(so.getStatus())) continue;
            if (branchId != null && !ReportService.branchMatches(so.getBranchId(), branchId, headquartersId)) continue;
            result.add(so);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CustomerSalesRow> salesByCustomer(Instant from, Instant to, Long branchId) {
        record Acc(String name, int[] counts, BigDecimal[] money) {
        }
        Map<Long, Acc> byCustomer = new LinkedHashMap<>();
        for (SalesOrder so : confirmedOrders(from, to, branchId)) {
            Customer customer = so.getCustomer();
            Long key = customer != null ? customer.getId() : -1L;
            Acc acc = byCustomer.computeIfAbsent(key, k -> new Acc(customer != null ? customer.getName() : "(no customer)",
                    new int[3], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.counts[0]++;
            for (SalesOrderLine line : so.getLines()) {
                acc.counts[1] += line.getQuantityOrdered();
                acc.counts[2] += line.getQuantityFulfilled();
            }
            acc.money[0] = acc.money[0].add(SalesOrderService.subtotalAmount(so));
            acc.money[1] = acc.money[1].add(SalesOrderService.taxAmount(so));
            acc.money[2] = acc.money[2].add(SalesOrderService.totalAmount(so));
            acc.money[3] = acc.money[3].add(customerCreditService.amountPaidForSalesOrder(so));
            acc.money[4] = acc.money[4].add(customerCreditService.outstandingForSalesOrder(so));
        }
        List<CustomerSalesRow> rows = new ArrayList<>();
        byCustomer.forEach((id, a) -> rows.add(new CustomerSalesRow(id < 0 ? null : id, a.name, a.counts[0], a.counts[1], a.counts[2],
                a.money[0], a.money[1], a.money[2], a.money[3], a.money[4])));
        rows.sort(Comparator.comparing(CustomerSalesRow::totalAmount).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ProductSalesOrderRow> salesByProduct(Instant from, Instant to, Long branchId) {
        record Acc(Long productId, String sku, String name, String category, int[] qty, BigDecimal[] money) {
        }
        Map<String, Acc> byProduct = new LinkedHashMap<>();
        for (SalesOrder so : confirmedOrders(from, to, branchId)) {
            for (SalesOrderLine line : so.getLines()) {
                Product product = line.getProduct();
                String key = product != null ? "p" + product.getId() : "n" + line.getProductName();
                Acc acc = byProduct.computeIfAbsent(key, k -> new Acc(product != null ? product.getId() : null,
                        product != null ? product.getSku() : null,
                        product != null ? product.getName() : line.getProductName(),
                        categoryName(product), new int[2], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
                acc.qty[0] += line.getQuantityOrdered();
                acc.qty[1] += line.getQuantityFulfilled();
                acc.money[0] = acc.money[0].add(SalesOrderService.lineNet(so.getDiscountPercent(), line));
                acc.money[1] = acc.money[1].add(SalesOrderService.lineTax(so.getDiscountPercent(), line));
            }
        }
        List<ProductSalesOrderRow> rows = new ArrayList<>();
        for (Acc a : byProduct.values()) {
            BigDecimal net = a.money[0].setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = a.money[1].setScale(2, RoundingMode.HALF_UP);
            BigDecimal average = a.qty[0] == 0 ? BigDecimal.ZERO : net.divide(BigDecimal.valueOf(a.qty[0]), 2, RoundingMode.HALF_UP);
            rows.add(new ProductSalesOrderRow(a.productId, a.sku, a.name, a.category, a.qty[0], a.qty[1], net, tax, net.add(tax), average));
        }
        rows.sort(Comparator.comparing(ProductSalesOrderRow::totalAmount).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<CategorySalesOrderRow> salesByCategory(Instant from, Instant to, Long branchId) {
        record Acc(int[] qty, BigDecimal[] money) {
        }
        Map<String, Acc> byCategory = new LinkedHashMap<>();
        for (SalesOrder so : confirmedOrders(from, to, branchId)) {
            for (SalesOrderLine line : so.getLines()) {
                Acc acc = byCategory.computeIfAbsent(categoryName(line.getProduct()),
                        k -> new Acc(new int[2], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
                acc.qty[0] += line.getQuantityOrdered();
                acc.qty[1] += line.getQuantityFulfilled();
                acc.money[0] = acc.money[0].add(SalesOrderService.lineNet(so.getDiscountPercent(), line));
                acc.money[1] = acc.money[1].add(SalesOrderService.lineTax(so.getDiscountPercent(), line));
            }
        }
        List<CategorySalesOrderRow> rows = new ArrayList<>();
        byCategory.forEach((name, a) -> {
            BigDecimal net = a.money[0].setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = a.money[1].setScale(2, RoundingMode.HALF_UP);
            rows.add(new CategorySalesOrderRow(name, a.qty[0], a.qty[1], net, tax, net.add(tax)));
        });
        rows.sort(Comparator.comparing(CategorySalesOrderRow::totalAmount).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<InvoiceReportRow> invoices(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<InvoiceReportRow> rows = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findAllByOrderByInvoiceDateDesc()) {
            if (invoice.getInvoiceDate().isBefore(from) || invoice.getInvoiceDate().isAfter(to)) continue;
            if (branchId != null && !ReportService.branchMatches(invoice.getBranchId(), branchId, headquartersId)) continue;
            rows.add(new InvoiceReportRow(invoice.getId(), invoice.getInvoiceNumber(), invoice.getSalesOrderNumber(),
                    invoice.getCustomerName(), invoice.getStatus().name(), invoice.getInvoiceDate(), invoice.getDueDate(),
                    invoice.getTotalAmount(), customerCreditService.amountPaidForInvoice(invoice),
                    customerCreditService.outstandingForInvoice(invoice)));
        }
        return rows;
    }

    /** Credit notes = voided invoices, by the date they were voided. */
    @Transactional(readOnly = true)
    public List<CreditNoteReportRow> creditNotes(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<CreditNoteReportRow> rows = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByStatusOrderByVoidedAtDesc(InvoiceStatus.VOID)) {
            Instant voidedAt = invoice.getVoidedAt();
            if (voidedAt == null || voidedAt.isBefore(from) || voidedAt.isAfter(to)) continue;
            if (branchId != null && !ReportService.branchMatches(invoice.getBranchId(), branchId, headquartersId)) continue;
            rows.add(new CreditNoteReportRow(invoice.getId(), invoice.getCreditNoteNumber(), invoice.getInvoiceNumber(),
                    invoice.getCustomerName(), voidedAt, invoice.getTotalAmount(), invoice.getVoidReason(),
                    invoice.getPaymentDisposition() != null ? invoice.getPaymentDisposition().name() : null));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<SalesReturnReportRow> salesReturns(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<SalesReturnReportRow> rows = new ArrayList<>();
        for (SalesOrderReturn ret : salesOrderReturnRepository.findByReturnDateBetweenOrderByReturnDateDesc(from, to)) {
            SalesOrder so = ret.getSalesOrder();
            if (branchId != null && !ReportService.branchMatches(so.getBranchId(), branchId, headquartersId)) continue;
            int quantity = 0;
            for (SalesOrderReturnLine line : ret.getLines()) {
                quantity += line.getQuantity();
            }
            Customer customer = so.getCustomer();
            rows.add(new SalesReturnReportRow(ret.getId(), ret.getReturnNumber(), so.getOrderNumber(),
                    customer != null ? customer.getName() : null, ret.getReturnDate(), ret.getReason(), quantity, ret.getRefundTotal()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<CustomerPaymentReportRow> customerPayments(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<CustomerPaymentReportRow> rows = new ArrayList<>();
        for (CustomerPayment payment : customerPaymentRepository.findAllByOrderByPaidAtDesc()) {
            if (payment.getPaidAt().isBefore(from) || payment.getPaidAt().isAfter(to)) continue;
            if (branchId != null && !ReportService.branchMatches(payment.getBranchId(), branchId, headquartersId)) continue;
            Customer customer = payment.getCustomer();
            rows.add(new CustomerPaymentReportRow(payment.getId(), payment.getReceiptNumber(),
                    customer != null ? customer.getName() : null, payment.getPaidAt(), payment.getMethod(), payment.getAmount(), payment.getNote()));
        }
        return rows;
    }

    /**
     * Snapshot, no period: what each customer owes today from invoices and sale orders, by age (the invoice date, or the
     * order date while the order has no invoice). POS credit sales have their own reports and are not included.
     */
    @Transactional(readOnly = true)
    public List<ReceivablesAgingRow> receivablesAging() {
        Instant now = Instant.now();
        Map<Long, AgeBuckets> byCustomer = new LinkedHashMap<>();
        for (SalesOrder so : salesOrderRepository.findAllByOrderByOrderDateDesc()) {
            BigDecimal owed = customerCreditService.outstandingForSalesOrder(so);
            if (owed.compareTo(BigDecimal.ZERO) <= 0) continue;
            Instant since = invoiceRepository.findBySalesOrderId(so.getId()).map(Invoice::getInvoiceDate).orElse(so.getOrderDate());
            Customer customer = so.getCustomer();
            addAged(byCustomer, customer != null ? customer.getId() : null, customer != null ? customer.getName() : null, owed, since, now);
        }
        // Invoices that did not come from a sale order (subscription / recurring invoices).
        for (Invoice invoice : invoiceRepository.findAllByOrderByInvoiceDateDesc()) {
            if (invoice.getSalesOrderId() != null) continue;
            BigDecimal owed = customerCreditService.outstandingForInvoice(invoice);
            if (owed.compareTo(BigDecimal.ZERO) <= 0) continue;
            addAged(byCustomer, invoice.getCustomerId(), invoice.getCustomerName(), owed, invoice.getInvoiceDate(), now);
        }
        List<ReceivablesAgingRow> rows = new ArrayList<>();
        byCustomer.forEach((id, a) -> rows.add(new ReceivablesAgingRow(id < 0 ? null : id, a.name(), a.buckets()[0], a.buckets()[1], a.buckets()[2],
                a.buckets()[3], a.buckets()[0].add(a.buckets()[1]).add(a.buckets()[2]).add(a.buckets()[3]))));
        rows.sort(Comparator.comparing(ReceivablesAgingRow::totalOutstanding).reversed());
        return rows;
    }

    private record AgeBuckets(String name, BigDecimal[] buckets) {
    }

    private static void addAged(Map<Long, AgeBuckets> map, Long customerId, String customerName, BigDecimal owed, Instant since, Instant now) {
        long days = Duration.between(since, now).toDays();
        int bucket = days <= 30 ? 0 : days <= 60 ? 1 : days <= 90 ? 2 : 3;
        AgeBuckets acc = map.computeIfAbsent(customerId != null ? customerId : -1L, k -> new AgeBuckets(
                customerName != null ? customerName : "(no customer)",
                new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}));
        acc.buckets()[bucket] = acc.buckets()[bucket].add(owed);
    }

    private static String categoryName(Product product) {
        Category category = product != null ? product.getCategory() : null;
        return category != null ? category.getName() : "Uncategorised";
    }
}
