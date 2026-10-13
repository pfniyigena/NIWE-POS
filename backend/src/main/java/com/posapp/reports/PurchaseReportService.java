package com.posapp.reports;

import com.posapp.bill.Bill;
import com.posapp.bill.BillRepository;
import com.posapp.branch.BranchService;
import com.posapp.catalog.Category;
import com.posapp.catalog.Product;
import com.posapp.inventory.PurchaseOrder;
import com.posapp.inventory.PurchaseOrderLine;
import com.posapp.inventory.PurchaseOrderRepository;
import com.posapp.inventory.PurchaseOrderStatus;
import com.posapp.inventory.PurchaseReturn;
import com.posapp.inventory.PurchaseReturnLine;
import com.posapp.inventory.PurchaseReturnRepository;
import com.posapp.inventory.Supplier;
import com.posapp.inventory.SupplierPayment;
import com.posapp.inventory.SupplierPaymentRepository;
import com.posapp.inventory.SupplierPaymentService;
import com.posapp.reports.dto.PurchaseReportRows.*;
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
 * The Purchases reports (Purchases > Reports): purchases by supplier / product / category, bills, purchase returns,
 * supplier payments and payables aging. Built like the other reports — read directly, group in Java. "Purchases" means
 * confirmed purchase orders (sent, partly or fully received); drafts and cancelled orders are not purchases. Amounts use
 * the same tax-inclusive cost the Purchase Order screens show ({@link SupplierPaymentService#totalCost}).
 */
@Service
public class PurchaseReportService {

    private static final List<PurchaseOrderStatus> CONFIRMED =
            List.of(PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BillRepository billRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierPaymentService supplierPaymentService;
    private final BranchService branchService;

    public PurchaseReportService(PurchaseOrderRepository purchaseOrderRepository, BillRepository billRepository,
                                 PurchaseReturnRepository purchaseReturnRepository,
                                 SupplierPaymentRepository supplierPaymentRepository,
                                 SupplierPaymentService supplierPaymentService, BranchService branchService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.billRepository = billRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.supplierPaymentRepository = supplierPaymentRepository;
        this.supplierPaymentService = supplierPaymentService;
        this.branchService = branchService;
    }

    /** Confirmed purchase orders in the period, optionally for one branch. */
    private List<PurchaseOrder> confirmedOrders(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<PurchaseOrder> result = new ArrayList<>();
        for (PurchaseOrder po : purchaseOrderRepository.findByOrderDateBetweenOrderByOrderDateDesc(from, to)) {
            if (!CONFIRMED.contains(po.getStatus())) continue;
            if (branchId != null && !ReportService.branchMatches(po.getBranchId(), branchId, headquartersId)) continue;
            result.add(po);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SupplierPurchaseRow> purchasesBySupplier(Instant from, Instant to, Long branchId) {
        record Acc(String name, int[] counts, BigDecimal[] money) {
        }
        Map<Long, Acc> bySupplier = new LinkedHashMap<>();
        for (PurchaseOrder po : confirmedOrders(from, to, branchId)) {
            Supplier supplier = po.getSupplier();
            Long key = supplier != null ? supplier.getId() : -1L;
            Acc acc = bySupplier.computeIfAbsent(key, k -> new Acc(supplier != null ? supplier.getName() : "(no supplier)",
                    new int[3], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.counts[0]++;
            for (PurchaseOrderLine line : po.getLines()) {
                acc.counts[1] += line.getQuantityOrdered();
                acc.counts[2] += line.getQuantityReceived();
            }
            acc.money[0] = acc.money[0].add(SupplierPaymentService.netCost(po));
            acc.money[1] = acc.money[1].add(SupplierPaymentService.taxCost(po));
            acc.money[2] = acc.money[2].add(SupplierPaymentService.totalCost(po));
            acc.money[3] = acc.money[3].add(supplierPaymentService.amountPaidForOrder(po));
            acc.money[4] = acc.money[4].add(supplierPaymentService.amountOwedForOrder(po));
        }
        List<SupplierPurchaseRow> rows = new ArrayList<>();
        bySupplier.forEach((id, a) -> rows.add(new SupplierPurchaseRow(id < 0 ? null : id, a.name, a.counts[0], a.counts[1], a.counts[2],
                a.money[0], a.money[1], a.money[2], a.money[3], a.money[4])));
        rows.sort(Comparator.comparing(SupplierPurchaseRow::totalCost).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ProductPurchaseRow> purchasesByProduct(Instant from, Instant to, Long branchId) {
        record Acc(Long productId, String sku, String name, String category, int[] qty, BigDecimal[] money) {
        }
        Map<String, Acc> byProduct = new LinkedHashMap<>();
        for (PurchaseOrder po : confirmedOrders(from, to, branchId)) {
            for (PurchaseOrderLine line : po.getLines()) {
                Product product = line.getProduct();
                String key = product != null ? "p" + product.getId() : "n" + line.getProductName();
                Acc acc = byProduct.computeIfAbsent(key, k -> new Acc(product != null ? product.getId() : null,
                        product != null ? product.getSku() : null,
                        product != null ? product.getName() : line.getProductName(),
                        categoryName(product), new int[2], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
                acc.qty[0] += line.getQuantityOrdered();
                acc.qty[1] += line.getQuantityReceived();
                acc.money[0] = acc.money[0].add(SupplierPaymentService.lineNet(po.getDiscountPercent(), line));
                acc.money[1] = acc.money[1].add(SupplierPaymentService.lineTax(po.getDiscountPercent(), line));
            }
        }
        List<ProductPurchaseRow> rows = new ArrayList<>();
        for (Acc a : byProduct.values()) {
            BigDecimal net = a.money[0].setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = a.money[1].setScale(2, RoundingMode.HALF_UP);
            BigDecimal average = a.qty[0] == 0 ? BigDecimal.ZERO : net.divide(BigDecimal.valueOf(a.qty[0]), 2, RoundingMode.HALF_UP);
            rows.add(new ProductPurchaseRow(a.productId, a.sku, a.name, a.category, a.qty[0], a.qty[1], net, tax, net.add(tax), average));
        }
        rows.sort(Comparator.comparing(ProductPurchaseRow::totalCost).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<CategoryPurchaseRow> purchasesByCategory(Instant from, Instant to, Long branchId) {
        record Acc(int[] qty, BigDecimal[] money) {
        }
        Map<String, Acc> byCategory = new LinkedHashMap<>();
        for (PurchaseOrder po : confirmedOrders(from, to, branchId)) {
            for (PurchaseOrderLine line : po.getLines()) {
                Acc acc = byCategory.computeIfAbsent(categoryName(line.getProduct()),
                        k -> new Acc(new int[2], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
                acc.qty[0] += line.getQuantityOrdered();
                acc.qty[1] += line.getQuantityReceived();
                acc.money[0] = acc.money[0].add(SupplierPaymentService.lineNet(po.getDiscountPercent(), line));
                acc.money[1] = acc.money[1].add(SupplierPaymentService.lineTax(po.getDiscountPercent(), line));
            }
        }
        List<CategoryPurchaseRow> rows = new ArrayList<>();
        byCategory.forEach((name, a) -> {
            BigDecimal net = a.money[0].setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = a.money[1].setScale(2, RoundingMode.HALF_UP);
            rows.add(new CategoryPurchaseRow(name, a.qty[0], a.qty[1], net, tax, net.add(tax)));
        });
        rows.sort(Comparator.comparing(CategoryPurchaseRow::totalCost).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<BillReportRow> bills(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<BillReportRow> rows = new ArrayList<>();
        for (Bill bill : billRepository.findAllByOrderByBillDateDesc()) {
            if (bill.getBillDate().isBefore(from) || bill.getBillDate().isAfter(to)) continue;
            if (branchId != null && !ReportService.branchMatches(bill.getBranchId(), branchId, headquartersId)) continue;
            rows.add(new BillReportRow(bill.getId(), bill.getBillNumber(), bill.getPurchaseOrderNumber(), bill.getSupplierName(),
                    bill.getStatus().name(), bill.getBillDate(), bill.getTotalAmount(),
                    supplierPaymentService.amountPaidForBill(bill), supplierPaymentService.outstandingForBill(bill)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PurchaseReturnReportRow> purchaseReturns(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<PurchaseReturnReportRow> rows = new ArrayList<>();
        for (PurchaseReturn ret : purchaseReturnRepository.findByReturnDateBetweenOrderByReturnDateDesc(from, to)) {
            PurchaseOrder po = ret.getPurchaseOrder();
            if (branchId != null && !ReportService.branchMatches(po.getBranchId(), branchId, headquartersId)) continue;
            int quantity = 0;
            for (PurchaseReturnLine line : ret.getLines()) {
                quantity += line.getQuantity();
            }
            Supplier supplier = po.getSupplier();
            rows.add(new PurchaseReturnReportRow(ret.getId(), ret.getReturnNumber(), po.getOrderNumber(),
                    supplier != null ? supplier.getName() : null, ret.getReturnDate(), ret.getReason(), quantity, ret.getRefundTotal()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<SupplierPaymentReportRow> supplierPayments(Instant from, Instant to, Long branchId) {
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        List<SupplierPaymentReportRow> rows = new ArrayList<>();
        for (SupplierPayment payment : supplierPaymentRepository.findAllByOrderByPaidAtDesc()) {
            if (payment.getPaidAt().isBefore(from) || payment.getPaidAt().isAfter(to)) continue;
            if (branchId != null && !ReportService.branchMatches(payment.getBranchId(), branchId, headquartersId)) continue;
            Supplier supplier = payment.getSupplier();
            rows.add(new SupplierPaymentReportRow(payment.getId(), payment.getReceiptNumber(),
                    supplier != null ? supplier.getName() : null, payment.getPaidAt(), payment.getMethod(), payment.getAmount(), payment.getNote()));
        }
        return rows;
    }

    /**
     * Snapshot, no period: what is owed to each supplier today, split by how old the order is (the bill's date once the
     * order has a bill). Only orders with something still owed count.
     */
    @Transactional(readOnly = true)
    public List<PayablesAgingRow> payablesAging() {
        Instant now = Instant.now();
        record Acc(String name, BigDecimal[] buckets) {
        }
        Map<Long, Acc> bySupplier = new LinkedHashMap<>();
        for (PurchaseOrder po : purchaseOrderRepository.findAllByOrderByOrderDateDesc()) {
            BigDecimal owed = supplierPaymentService.amountOwedForOrder(po);
            if (owed.compareTo(BigDecimal.ZERO) <= 0) continue;
            Instant since = billRepository.findByPurchaseOrderId(po.getId()).map(Bill::getBillDate).orElse(po.getOrderDate());
            long days = Duration.between(since, now).toDays();
            int bucket = days <= 30 ? 0 : days <= 60 ? 1 : days <= 90 ? 2 : 3;
            Supplier supplier = po.getSupplier();
            Long key = supplier != null ? supplier.getId() : -1L;
            Acc acc = bySupplier.computeIfAbsent(key, k -> new Acc(supplier != null ? supplier.getName() : "(no supplier)",
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.buckets[bucket] = acc.buckets[bucket].add(owed);
        }
        List<PayablesAgingRow> rows = new ArrayList<>();
        bySupplier.forEach((id, a) -> rows.add(new PayablesAgingRow(id < 0 ? null : id, a.name, a.buckets[0], a.buckets[1], a.buckets[2],
                a.buckets[3], a.buckets[0].add(a.buckets[1]).add(a.buckets[2]).add(a.buckets[3]))));
        rows.sort(Comparator.comparing(PayablesAgingRow::totalOutstanding).reversed());
        return rows;
    }

    private static String categoryName(Product product) {
        Category category = product != null ? product.getCategory() : null;
        return category != null ? category.getName() : "Uncategorised";
    }
}
