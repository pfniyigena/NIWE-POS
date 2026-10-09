package com.posapp.reports;

import com.posapp.branch.BranchService;
import com.posapp.catalog.Brand;
import com.posapp.catalog.Category;
import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import com.posapp.common.ApiException;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.CustomerPayment;
import com.posapp.credit.CustomerPaymentRepository;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.inventory.PurchaseOrder;
import com.posapp.inventory.PurchaseOrderLine;
import com.posapp.inventory.PurchaseOrderRepository;
import com.posapp.inventory.PurchaseReturn;
import com.posapp.inventory.PurchaseReturnLine;
import com.posapp.inventory.PurchaseReturnRepository;
import com.posapp.inventory.StockAdjustment;
import com.posapp.inventory.StockAdjustmentRepository;
import com.posapp.inventory.StockReceipt;
import com.posapp.inventory.StockReceiptRepository;
import com.posapp.inventory.Supplier;
import com.posapp.inventory.SupplierPaymentService;
import com.posapp.inventory.SupplierRepository;
import com.posapp.manufacturing.ManufacturingOrderRun;
import com.posapp.manufacturing.ManufacturingOrderRunRepository;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderStatus;
import com.posapp.order.Payment;
import com.posapp.order.SaleOrder;
import com.posapp.packaging.PackagingBreakdown;
import com.posapp.packaging.PackagingBreakdownRepository;
import com.posapp.register.RegisterSession;
import com.posapp.restaurant.MenuCategory;
import com.posapp.restaurant.MenuItem;
import com.posapp.restaurant.MenuItemRepository;
import com.posapp.restaurant.RestaurantOrder;
import com.posapp.restaurant.RestaurantOrderCalculator;
import com.posapp.restaurant.RestaurantOrderConsumption;
import com.posapp.restaurant.RestaurantOrderLine;
import com.posapp.restaurant.RestaurantOrderRepository;
import com.posapp.restaurant.RestaurantOrderStatus;
import com.posapp.restaurant.RestaurantPayment;
import com.posapp.restaurant.RestaurantWastage;
import com.posapp.restaurant.RestaurantWastageRepository;
import com.posapp.restaurant.RestaurantWastageStatus;
import com.posapp.user.AppUser;
import com.posapp.returns.SalesReturn;
import com.posapp.returns.SalesReturnLine;
import com.posapp.returns.SalesReturnRepository;
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
import com.posapp.reports.dto.StockAgingRow;
import com.posapp.reports.dto.StockMovementRow;
import com.posapp.reports.dto.StockValuationRow;
import com.posapp.warehouse.WarehouseService;
import com.posapp.reports.dto.SupplierStatementRow;
import com.posapp.salesorder.SalesOrder;
import com.posapp.salesorder.SalesOrderFulfillment;
import com.posapp.salesorder.SalesOrderFulfillmentRepository;
import com.posapp.salesorder.SalesOrderRepository;
import com.posapp.salesorder.SalesOrderReturn;
import com.posapp.salesorder.SalesOrderReturnLine;
import com.posapp.salesorder.SalesOrderReturnRepository;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.SalesOrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The seven POS Reports (Task: "Add POS Report where it will show the list of the following
 * reports ... by click on each report it will open report with different filters"). Every report
 * is built from the same source — {@code OrderRepository.findCompletedForReport}, every completed
 * sale in a date range, optionally narrowed to one register — grouped in Java the same way
 * {@code OrderService.aggregate} already does for the Z-report, rather than in SQL, since this
 * codebase's convention is to keep money math (see OrderCalculator) out of queries. Every method
 * is {@code @Transactional(readOnly = true)} over its *entire* body (not just the repository
 * call), because line/product/category are all LAZY associations still being walked during the
 * grouping loop below — see CatalogService's class javadoc for why that matters.
 * <p>
 * "Profit" throughout means revenue (post-discount, pre-tax — see OrderCalculator.lineNetTotal;
 * for an inclusive-tax line this backs the embedded tax back out, same as it does for the
 * subtotal on a receipt) minus cost of goods (each line's snapshotted {@code unitCost} ×
 * quantity — see OrderLine's javadoc). Sales tax collected is never counted as revenue or
 * profit; it's money held for remittance, not income. The Sales reports (categorySales,
 * productSales) use the same {@code lineNetTotal} + {@code lineTax} split for the same reason —
 * summing raw {@code lineTotal} for an inclusive-tax line would double-count its embedded tax
 * once {@code lineTax} is added back on for the grand total.
 * <p>
 * Task 1: "Inventory Report" added two more reports below ({@link #stockValuation} and {@link
 * #stockMovementHistory}) that were NOT built from {@code OrderRepository} at all — they read
 * {@code Product}/{@code StockAdjustment}/{@code StockReceipt} directly, since neither is a sales
 * report. Tasks 4/5 ({@link #purchaseOrders}/{@link #supplierStatement}) are a third family
 * again, reading {@code PurchaseOrderRepository} directly — purchasing, not selling. Task 6:
 * "Any transaction affecting stock should be shown to Stock Movement History" widened {@link
 * #stockMovementHistory} to also merge in POS sales/returns, Sale Order fulfillments/returns, and
 * Purchase returns; Fourier's later "Restaurant should impact Inventory Module" added a checked-out
 * Restaurant order's own lines and recipe-backed ingredient consumptions — see that method's own
 * javadoc for the full list of sources it now reads.
 * <p>
 * Fourier: "Add all reports related to Restaurants Including all type of orders etc" added a
 * fourth family of reports (see the "Restaurant reports" section below, {@link
 * #restaurantOrderTypeSales} through {@link #restaurantCancelledOrders}) — built the same way as
 * the POS Sales/Profit reports above (read directly, group in Java), just from {@code
 * RestaurantOrderRepository} instead of {@code OrderRepository}/{@code SaleOrder}, and with no
 * register filter (a restaurant order isn't tied to one). Every DINE_IN/TAKEAWAY/DELIVERY order is
 * included in each report — nothing here is dine-in-only. Fourier's later "Restaurants need a
 * dedicated wastage module" added one more, {@link #restaurantWastage}, plus a matching
 * "Restaurant wastage" source in {@link #stockMovementHistory} itself.
 */
@Service
public class ReportService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerCreditService customerCreditService;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final ProductRepository productRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockReceiptRepository stockReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPaymentService supplierPaymentService;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final SalesOrderFulfillmentRepository salesOrderFulfillmentRepository;
    private final SalesOrderReturnRepository salesOrderReturnRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final ManufacturingOrderRunRepository manufacturingOrderRunRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantWastageRepository restaurantWastageRepository;
    private final PackagingBreakdownRepository packagingBreakdownRepository;
    /** Multi-warehouse phase 1 — backs the warehouse filter on {@link #stockValuation}. One-directional dependency: WarehouseService depends on nothing from reports. */
    private final WarehouseService warehouseService;
    /** Branch filter on the POS reports — resolves which branch a legacy (null) record belongs to. Independent of everything else here, so no cycle. */
    private final BranchService branchService;
    /** Reserved quantities for the Stock On Hand report. */
    private final com.posapp.salesorder.StockReservationService stockReservationService;
    /** In-transit quantities for the Stock On Hand report. */
    private final com.posapp.warehouse.StockTransferRepository stockTransferRepository;
    /** Same config property (and default) DashboardService/CatalogController already use for "what counts as low stock" — see StockValuationRow.status. */
    private final int lowStockThreshold;

    public ReportService(OrderRepository orderRepository,
                          CustomerRepository customerRepository,
                          CustomerCreditService customerCreditService,
                          CustomerPaymentRepository customerPaymentRepository,
                          ProductRepository productRepository,
                          StockAdjustmentRepository stockAdjustmentRepository,
                          StockReceiptRepository stockReceiptRepository,
                          PurchaseOrderRepository purchaseOrderRepository,
                          SupplierRepository supplierRepository,
                          SupplierPaymentService supplierPaymentService,
                          SalesOrderRepository salesOrderRepository,
                          SalesReturnRepository salesReturnRepository,
                          SalesOrderFulfillmentRepository salesOrderFulfillmentRepository,
                          SalesOrderReturnRepository salesOrderReturnRepository,
                          PurchaseReturnRepository purchaseReturnRepository,
                          ManufacturingOrderRunRepository manufacturingOrderRunRepository,
                          RestaurantOrderRepository restaurantOrderRepository,
                          MenuItemRepository menuItemRepository,
                          RestaurantWastageRepository restaurantWastageRepository,
                          PackagingBreakdownRepository packagingBreakdownRepository,
                          WarehouseService warehouseService,
                          BranchService branchService,
                          com.posapp.salesorder.StockReservationService stockReservationService,
                          com.posapp.warehouse.StockTransferRepository stockTransferRepository,
                          @Value("${app.pos.low-stock-threshold:10}") int lowStockThreshold) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.customerCreditService = customerCreditService;
        this.customerPaymentRepository = customerPaymentRepository;
        this.productRepository = productRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.stockReceiptRepository = stockReceiptRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.supplierPaymentService = supplierPaymentService;
        this.salesOrderRepository = salesOrderRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.salesOrderFulfillmentRepository = salesOrderFulfillmentRepository;
        this.salesOrderReturnRepository = salesOrderReturnRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.manufacturingOrderRunRepository = manufacturingOrderRunRepository;
        this.restaurantOrderRepository = restaurantOrderRepository;
        this.menuItemRepository = menuItemRepository;
        this.restaurantWastageRepository = restaurantWastageRepository;
        this.packagingBreakdownRepository = packagingBreakdownRepository;
        this.warehouseService = warehouseService;
        this.branchService = branchService;
        this.stockReservationService = stockReservationService;
        this.stockTransferRepository = stockTransferRepository;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Transactional(readOnly = true)
    public List<CategorySalesRow> categorySales(Instant from, Instant to, Long registerId, Long branchId) {
        record Acc(String name, int[] qty, BigDecimal[] sales, BigDecimal[] tax) {}
        Map<Long, Acc> byCategory = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            for (OrderLine line : order.getLines()) {
                Category category = line.getProduct() != null ? line.getProduct().getCategory() : null;
                Long key = category != null ? category.getId() : -1L;
                String name = category != null ? category.getName() : "Uncategorized";
                Acc acc = byCategory.computeIfAbsent(key, k -> new Acc(name, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.sales()[0] = acc.sales()[0].add(OrderCalculator.lineNetTotal(line));
                acc.tax()[0] = acc.tax()[0].add(OrderCalculator.lineTax(line));
            }
        }
        List<CategorySalesRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byCategory.entrySet()) {
            Acc a = e.getValue();
            BigDecimal grand = a.sales()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new CategorySalesRow(e.getKey() == -1L ? null : e.getKey(), a.name(), a.qty()[0], a.sales()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(CategorySalesRow::salesTotal).reversed());
        return rows;
    }

    /**
     * "Total POS Brands Sales" (Fourier: "Add also report related to brand") — the same grouping
     * as {@link #categorySales} one level over: by the product's brand instead of its category,
     * "No Brand" instead of "Uncategorized" for a product with none assigned.
     */
    @Transactional(readOnly = true)
    public List<BrandSalesRow> brandSales(Instant from, Instant to, Long registerId, Long branchId) {
        record Acc(String name, int[] qty, BigDecimal[] sales, BigDecimal[] tax) {}
        Map<Long, Acc> byBrand = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            for (OrderLine line : order.getLines()) {
                Brand brand = line.getProduct() != null ? line.getProduct().getBrand() : null;
                Long key = brand != null ? brand.getId() : -1L;
                String name = brand != null ? brand.getName() : "No Brand";
                Acc acc = byBrand.computeIfAbsent(key, k -> new Acc(name, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.sales()[0] = acc.sales()[0].add(OrderCalculator.lineNetTotal(line));
                acc.tax()[0] = acc.tax()[0].add(OrderCalculator.lineTax(line));
            }
        }
        List<BrandSalesRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byBrand.entrySet()) {
            Acc a = e.getValue();
            BigDecimal grand = a.sales()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new BrandSalesRow(e.getKey() == -1L ? null : e.getKey(), a.name(), a.qty()[0], a.sales()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(BrandSalesRow::salesTotal).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ProductSalesRow> productSales(Instant from, Instant to, Long registerId, Long branchId, Long categoryId) {
        record Acc(String sku, String name, String categoryName, int[] qty, BigDecimal[] sales, BigDecimal[] tax) {}
        Map<Long, Acc> byProduct = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            for (OrderLine line : order.getLines()) {
                Product product = line.getProduct();
                if (product == null) continue;
                if (categoryId != null && (product.getCategory() == null || !categoryId.equals(product.getCategory().getId()))) {
                    continue;
                }
                Acc acc = byProduct.computeIfAbsent(product.getId(), k -> new Acc(
                        product.getSku(), product.getName(),
                        product.getCategory() != null ? product.getCategory().getName() : "Uncategorized",
                        new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.sales()[0] = acc.sales()[0].add(OrderCalculator.lineNetTotal(line));
                acc.tax()[0] = acc.tax()[0].add(OrderCalculator.lineTax(line));
            }
        }
        List<ProductSalesRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byProduct.entrySet()) {
            Acc a = e.getValue();
            BigDecimal grand = a.sales()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new ProductSalesRow(e.getKey(), a.sku(), a.name(), a.categoryName(), a.qty()[0], a.sales()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(ProductSalesRow::salesTotal).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ShiftSalesRow> shiftSales(Instant from, Instant to, Long registerId, Long branchId) {
        record Acc(RegisterSession shift, int[] count, BigDecimal[] sub, BigDecimal[] tax) {}
        Map<Long, Acc> byShift = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            RegisterSession shift = order.getSession();
            if (shift == null) continue;
            Acc acc = byShift.computeIfAbsent(shift.getId(), k -> new Acc(shift, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
            acc.count()[0]++;
            acc.sub()[0] = acc.sub()[0].add(OrderCalculator.subtotal(order));
            acc.tax()[0] = acc.tax()[0].add(OrderCalculator.taxTotal(order));
        }
        List<ShiftSalesRow> rows = new ArrayList<>();
        for (Acc a : byShift.values()) {
            RegisterSession s = a.shift();
            BigDecimal grand = a.sub()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new ShiftSalesRow(
                    s.getId(),
                    s.getRegister() != null ? s.getRegister().getName() : "—",
                    s.getCashier() != null ? s.getCashier().getFullName() : "—",
                    s.getOpenedAt(), s.getClosedAt(), s.getStatus().name(),
                    a.count()[0], a.sub()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(ShiftSalesRow::openedAt));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ShiftTransactionRow> shiftTransactions(Instant from, Instant to, Long registerId, Long branchId) {
        List<ShiftTransactionRow> rows = new ArrayList<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            RegisterSession shift = order.getSession();
            var cashierSession = order.getCashierSession();
            var cashier = cashierSession != null ? cashierSession.getCashier()
                    : (shift != null ? shift.getCashier() : null);
            var customer = order.getCustomer();
            String methods = order.getPayments().stream().map(Payment::getMethod)
                    .reduce((a, b) -> a + " + " + b).orElse("—");
            rows.add(new ShiftTransactionRow(
                    order.getId(),
                    shift != null ? shift.getId() : null,
                    shift != null && shift.getRegister() != null ? shift.getRegister().getName() : "—",
                    cashier != null ? cashier.getFullName() : "—",
                    customer != null ? customer.getName() : "Walk-in Customer",
                    order.getInvoiceNumber(),
                    order.getCompletedAt(),
                    OrderCalculator.totalQuantity(order),
                    OrderCalculator.subtotal(order),
                    OrderCalculator.taxTotal(order),
                    OrderCalculator.total(order),
                    methods));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ShiftProfitRow> shiftProfit(Instant from, Instant to, Long registerId, Long branchId) {
        record Acc(RegisterSession shift, int[] count, BigDecimal[] revenue, BigDecimal[] cost) {}
        Map<Long, Acc> byShift = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            RegisterSession shift = order.getSession();
            if (shift == null) continue;
            Acc acc = byShift.computeIfAbsent(shift.getId(), k -> new Acc(shift, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
            acc.count()[0]++;
            for (OrderLine line : order.getLines()) {
                acc.revenue()[0] = acc.revenue()[0].add(OrderCalculator.lineNetTotal(line));
                acc.cost()[0] = acc.cost()[0].add(OrderCalculator.lineCost(line));
            }
        }
        List<ShiftProfitRow> rows = new ArrayList<>();
        for (Acc a : byShift.values()) {
            RegisterSession s = a.shift();
            BigDecimal profit = a.revenue()[0].subtract(a.cost()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new ShiftProfitRow(
                    s.getId(),
                    s.getRegister() != null ? s.getRegister().getName() : "—",
                    s.getCashier() != null ? s.getCashier().getFullName() : "—",
                    s.getOpenedAt(), s.getClosedAt(),
                    a.count()[0], a.revenue()[0], a.cost()[0], profit,
                    marginPercent(profit, a.revenue()[0])));
        }
        rows.sort(Comparator.comparing(ShiftProfitRow::openedAt));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<CategoryProfitRow> categoryProfit(Instant from, Instant to, Long registerId, Long branchId) {
        record Acc(String name, int[] qty, BigDecimal[] revenue, BigDecimal[] cost) {}
        Map<Long, Acc> byCategory = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            for (OrderLine line : order.getLines()) {
                Category category = line.getProduct() != null ? line.getProduct().getCategory() : null;
                Long key = category != null ? category.getId() : -1L;
                String name = category != null ? category.getName() : "Uncategorized";
                Acc acc = byCategory.computeIfAbsent(key, k -> new Acc(name, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.revenue()[0] = acc.revenue()[0].add(OrderCalculator.lineNetTotal(line));
                acc.cost()[0] = acc.cost()[0].add(OrderCalculator.lineCost(line));
            }
        }
        List<CategoryProfitRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byCategory.entrySet()) {
            Acc a = e.getValue();
            BigDecimal profit = a.revenue()[0].subtract(a.cost()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new CategoryProfitRow(e.getKey() == -1L ? null : e.getKey(), a.name(), a.qty()[0], a.revenue()[0], a.cost()[0], profit, marginPercent(profit, a.revenue()[0])));
        }
        rows.sort(Comparator.comparing(CategoryProfitRow::profit).reversed());
        return rows;
    }

    /**
     * "Total POS Brands Profit" (Fourier: "Add also report related to brand") — the same grouping
     * as {@link #categoryProfit} one level over: by the product's brand instead of its category.
     */
    @Transactional(readOnly = true)
    public List<BrandProfitRow> brandProfit(Instant from, Instant to, Long registerId, Long branchId) {
        record Acc(String name, int[] qty, BigDecimal[] revenue, BigDecimal[] cost) {}
        Map<Long, Acc> byBrand = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            for (OrderLine line : order.getLines()) {
                Brand brand = line.getProduct() != null ? line.getProduct().getBrand() : null;
                Long key = brand != null ? brand.getId() : -1L;
                String name = brand != null ? brand.getName() : "No Brand";
                Acc acc = byBrand.computeIfAbsent(key, k -> new Acc(name, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.revenue()[0] = acc.revenue()[0].add(OrderCalculator.lineNetTotal(line));
                acc.cost()[0] = acc.cost()[0].add(OrderCalculator.lineCost(line));
            }
        }
        List<BrandProfitRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byBrand.entrySet()) {
            Acc a = e.getValue();
            BigDecimal profit = a.revenue()[0].subtract(a.cost()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new BrandProfitRow(e.getKey() == -1L ? null : e.getKey(), a.name(), a.qty()[0], a.revenue()[0], a.cost()[0], profit, marginPercent(profit, a.revenue()[0])));
        }
        rows.sort(Comparator.comparing(BrandProfitRow::profit).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ProductProfitRow> productProfit(Instant from, Instant to, Long registerId, Long branchId, Long categoryId) {
        record Acc(String sku, String name, String categoryName, int[] qty, BigDecimal[] revenue, BigDecimal[] cost) {}
        Map<Long, Acc> byProduct = new LinkedHashMap<>();
        for (SaleOrder order : completedOrders(from, to, registerId, branchId)) {
            for (OrderLine line : order.getLines()) {
                Product product = line.getProduct();
                if (product == null) continue;
                if (categoryId != null && (product.getCategory() == null || !categoryId.equals(product.getCategory().getId()))) {
                    continue;
                }
                Acc acc = byProduct.computeIfAbsent(product.getId(), k -> new Acc(
                        product.getSku(), product.getName(),
                        product.getCategory() != null ? product.getCategory().getName() : "Uncategorized",
                        new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.revenue()[0] = acc.revenue()[0].add(OrderCalculator.lineNetTotal(line));
                acc.cost()[0] = acc.cost()[0].add(OrderCalculator.lineCost(line));
            }
        }
        List<ProductProfitRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byProduct.entrySet()) {
            Acc a = e.getValue();
            BigDecimal profit = a.revenue()[0].subtract(a.cost()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new ProductProfitRow(e.getKey(), a.sku(), a.name(), a.categoryName(), a.qty()[0], a.revenue()[0], a.cost()[0], profit, marginPercent(profit, a.revenue()[0])));
        }
        rows.sort(Comparator.comparing(ProductProfitRow::profit).reversed());
        return rows;
    }

    /**
     * The Customer Statement (Task: "Add Customers Report (Customer Statement)") — every
     * completed sale one specific customer made in the date range, oldest first. 404s if the
     * customer id doesn't exist, same convention every other id-in-the-path lookup in this
     * codebase uses, rather than silently returning an empty statement for a typo'd id.
     */
    @Transactional(readOnly = true)
    public List<CustomerStatementRow> customerStatement(Long customerId, Instant from, Instant to) {
        if (!customerRepository.existsById(customerId)) {
            throw ApiException.notFound("Customer not found: " + customerId);
        }
        List<CustomerStatementRow> rows = new ArrayList<>();
        for (SaleOrder order : orderRepository.findCompletedForCustomerStatement(customerId, OrderStatus.COMPLETED, from, to)) {
            RegisterSession shift = order.getSession();
            var cashierSession = order.getCashierSession();
            var cashier = cashierSession != null ? cashierSession.getCashier()
                    : (shift != null ? shift.getCashier() : null);
            String methods = order.getPayments().stream().map(Payment::getMethod)
                    .reduce((a, b) -> a + " + " + b).orElse("—");
            rows.add(new CustomerStatementRow(
                    order.getId(),
                    order.getInvoiceNumber(),
                    order.getCompletedAt(),
                    shift != null && shift.getRegister() != null ? shift.getRegister().getName() : "—",
                    cashier != null ? cashier.getFullName() : "—",
                    OrderCalculator.totalQuantity(order),
                    OrderCalculator.subtotal(order),
                    OrderCalculator.taxTotal(order),
                    OrderCalculator.total(order),
                    methods,
                    order.getCreditAmount(),
                    order.getDueDate(),
                    order.getCreditAmount() != null ? customerCreditService.outstandingForOrder(order) : null));
        }
        // Task: "Add Sale order and Customer payment... so it shows up alongside register sales in
        // one place" — merge this customer's own Sales Orders into the same chronological
        // statement, not just POS sales. registerName/cashierName/paymentMethods have no Sales
        // Order equivalent (no register/cashier session — see SalesOrder's class javadoc), so those
        // columns read "—"/"Sales order" instead; subtotal/taxTotal aren't modeled on a Sales Order
        // at all (see SalesOrder's javadoc on skipping tax), so grandTotal alone carries the amount
        // and the other two are zero.
        for (SalesOrder so : salesOrderRepository.findAllByOrderByOrderDateDesc()) {
            if (so.getCustomer() == null || !so.getCustomer().getId().equals(customerId)) continue;
            if (so.getOrderDate().isBefore(from) || so.getOrderDate().isAfter(to)) continue;
            int qty = so.getLines().stream().mapToInt(l -> l.getQuantityOrdered()).sum();
            BigDecimal total = SalesOrderService.totalAmount(so);
            rows.add(new CustomerStatementRow(
                    so.getId(),
                    so.getOrderNumber(),
                    so.getOrderDate(),
                    "—",
                    "Sales order",
                    qty,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    total,
                    "—",
                    total,
                    so.getDueDate(),
                    customerCreditService.outstandingForSalesOrder(so)));
        }
        rows.sort(Comparator.comparing(CustomerStatementRow::completedAt));
        return rows;
    }

    /** Task 16: "Credit sale report" — every completed sale with a credit portion, any customer, in the date range. */
    @Transactional(readOnly = true)
    public List<CreditSaleRow> creditSales(Instant from, Instant to, Long branchId) {
        List<CreditSaleRow> rows = new ArrayList<>();
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        for (SaleOrder order : orderRepository.findAllCreditOrders(OrderStatus.COMPLETED, from, to)) {
            if (branchId != null && !branchMatches(order.getBranchId(), branchId, headquartersId)) continue;
            Customer customer = order.getCustomer();
            rows.add(new CreditSaleRow(
                    order.getId(),
                    order.getInvoiceNumber(),
                    order.getCompletedAt(),
                    customer != null ? customer.getId() : null,
                    customer != null ? customer.getName() : "Walk-in Customer",
                    order.getCreditAmount(),
                    customerCreditService.outstandingForOrder(order),
                    order.getDueDate()));
        }
        return rows;
    }

    /** Task 17: "Daily credit collection report" — every customer payment recorded in the date range, summed per calendar day (UTC, matching every other report's day boundary). */
    @Transactional(readOnly = true)
    public List<DailyCreditCollectionRow> dailyCreditCollection(Instant from, Instant to, Long branchId) {
        record Acc(int[] count, BigDecimal[] total) {}
        Map<LocalDate, Acc> byDay = new LinkedHashMap<>();
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        for (CustomerPayment payment : customerPaymentRepository.findByPaidAtBetweenOrderByPaidAtAsc(from, to)) {
            if (branchId != null && !branchMatches(payment.getBranchId(), branchId, headquartersId)) continue;
            LocalDate day = payment.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate();
            Acc acc = byDay.computeIfAbsent(day, k -> new Acc(new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}));
            acc.count()[0]++;
            acc.total()[0] = acc.total()[0].add(payment.getAmount());
        }
        List<DailyCreditCollectionRow> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, Acc> e : byDay.entrySet()) {
            rows.add(new DailyCreditCollectionRow(e.getKey(), e.getValue().count()[0], e.getValue().total()[0]));
        }
        rows.sort(Comparator.comparing(DailyCreditCollectionRow::date));
        return rows;
    }

    /**
     * Task 10: "Outstanding credit report" — snapshot, no date range: one row per customer who
     * currently owes anything, built from every credit sale ever made (not scoped by
     * Customer.creditEnabled, so a customer whose credit was since disabled still shows what they
     * owe from before).
     */
    @Transactional(readOnly = true)
    public List<OutstandingCreditRow> outstandingCredit(Long branchId) {
        // outstanding[0] = what this report shows (only the chosen branch's sales when a branch is picked);
        // outstanding[1] = the customer's whole balance, which is what their credit limit is measured against.
        record Acc(Customer customer, BigDecimal[] outstanding) {}
        Map<Long, Acc> byCustomer = new LinkedHashMap<>();
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        for (SaleOrder order : orderRepository.findAllCreditOrders(OrderStatus.COMPLETED)) {
            Customer customer = order.getCustomer();
            if (customer == null) continue;
            BigDecimal outstandingForOrder = customerCreditService.outstandingForOrder(order);
            Acc acc = byCustomer.computeIfAbsent(customer.getId(), k -> new Acc(customer, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.outstanding()[1] = acc.outstanding()[1].add(outstandingForOrder);
            if (branchId == null || branchMatches(order.getBranchId(), branchId, headquartersId)) {
                acc.outstanding()[0] = acc.outstanding()[0].add(outstandingForOrder);
            }
        }
        // Merge in Sales Orders' own outstanding amounts — see customerStatement's identical reasoning.
        for (SalesOrder so : salesOrderRepository.findAllByOrderByOrderDateDesc()) {
            Customer customer = so.getCustomer();
            if (customer == null) continue;
            BigDecimal outstandingForSalesOrder = customerCreditService.outstandingForSalesOrder(so);
            if (outstandingForSalesOrder.compareTo(BigDecimal.ZERO) <= 0) continue;
            Acc acc = byCustomer.computeIfAbsent(customer.getId(), k -> new Acc(customer, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.outstanding()[1] = acc.outstanding()[1].add(outstandingForSalesOrder);
            if (branchId == null || branchMatches(so.getBranchId(), branchId, headquartersId)) {
                acc.outstanding()[0] = acc.outstanding()[0].add(outstandingForSalesOrder);
            }
        }
        List<OutstandingCreditRow> rows = new ArrayList<>();
        for (Acc a : byCustomer.values()) {
            if (a.outstanding()[0].compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal available = a.customer().getCreditLimit().subtract(a.outstanding()[1]);
            if (available.compareTo(BigDecimal.ZERO) < 0) available = BigDecimal.ZERO;
            rows.add(new OutstandingCreditRow(
                    a.customer().getId(), a.customer().getName(), a.customer().getPhone(),
                    a.customer().getCreditLimit(), a.outstanding()[0], available));
        }
        rows.sort(Comparator.comparing(OutstandingCreditRow::outstandingBalance).reversed());
        return rows;
    }

    /**
     * Task 11: "Overdue credit report" — snapshot, no date range: one row per still-outstanding
     * credit sale whose due date has already passed (never includes a sale with no due date at
     * all — see SaleOrder.dueDate's javadoc).
     */
    @Transactional(readOnly = true)
    public List<OverdueCreditRow> overdueCredit(Long branchId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<OverdueCreditRow> rows = new ArrayList<>();
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        for (SaleOrder order : orderRepository.findAllCreditOrders(OrderStatus.COMPLETED)) {
            if (order.getDueDate() == null || !order.getDueDate().isBefore(today)) continue;
            if (branchId != null && !branchMatches(order.getBranchId(), branchId, headquartersId)) continue;
            BigDecimal outstanding = customerCreditService.outstandingForOrder(order);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;
            Customer customer = order.getCustomer();
            rows.add(new OverdueCreditRow(
                    order.getId(),
                    order.getInvoiceNumber(),
                    customer != null ? customer.getId() : null,
                    customer != null ? customer.getName() : "Walk-in Customer",
                    customer != null ? customer.getPhone() : null,
                    order.getDueDate(),
                    java.time.temporal.ChronoUnit.DAYS.between(order.getDueDate(), today),
                    order.getCreditAmount(),
                    outstanding));
        }
        // Merge in overdue Sales Orders — same "no due date means never overdue" floor as above.
        for (SalesOrder so : salesOrderRepository.findAllByOrderByOrderDateDesc()) {
            if (so.getDueDate() == null || !so.getDueDate().isBefore(today)) continue;
            if (branchId != null && !branchMatches(so.getBranchId(), branchId, headquartersId)) continue;
            BigDecimal outstanding = customerCreditService.outstandingForSalesOrder(so);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;
            Customer customer = so.getCustomer();
            rows.add(new OverdueCreditRow(
                    so.getId(),
                    so.getOrderNumber(),
                    customer != null ? customer.getId() : null,
                    customer != null ? customer.getName() : "Walk-in Customer",
                    customer != null ? customer.getPhone() : null,
                    so.getDueDate(),
                    java.time.temporal.ChronoUnit.DAYS.between(so.getDueDate(), today),
                    SalesOrderService.totalAmount(so),
                    outstanding));
        }
        rows.sort(Comparator.comparing(OverdueCreditRow::daysOverdue).reversed());
        return rows;
    }

    /**
     * Task 1: "Inventory Report" — "Stock on hand & valuation" half. One row per active product,
     * as of right now (no date range — see OutstandingCreditRow's javadoc for the same "snapshot"
     * reasoning). {@code categoryId} narrows it the same way productSales/productProfit's does.
     */
    /**
     * Multi-warehouse phase 1: the quantity column now has three modes. Neither argument set (the
     * original behavior, unchanged): {@code Product.stockQuantity}, i.e. the primary warehouse's
     * sales-location stock. {@code warehouseId} set: that one warehouse's sellable stock (active,
     * non-DAMAGED locations — RECEIVING and STORAGE included, DAMAGED excluded). {@code
     * allWarehouses} set: the same sellable definition summed across every warehouse. Value is
     * always quantity x unit cost. Note stock sitting in a RECEIVING location is counted in the two
     * warehouse modes but not in the default mode, since the default mode only ever saw sales stock.
     */
    @Transactional(readOnly = true)
    public List<StockValuationRow> stockValuation(Long categoryId, Long warehouseId, boolean allWarehouses, Long branchId) {
        // A chosen warehouse wins over a branch; otherwise a branch sums its warehouses' sellable stock.
        boolean branchMode = branchId != null && warehouseId == null;
        java.util.Map<Long, Integer> scopedQuantities = branchMode
                ? warehouseService.sellableQuantitiesByProductForBranch(branchId)
                : (warehouseId != null || allWarehouses)
                ? warehouseService.sellableQuantitiesByProduct(warehouseId)
                : null;
        // "Low stock" uses the threshold of whichever warehouse the quantity column describes: a
        // chosen warehouse's own, the primary's in the default view, the store-wide default across all.
        int threshold = (allWarehouses || branchMode) ? lowStockThreshold
                : warehouseId != null ? warehouseService.effectiveLowStockThreshold(warehouseService.getEntityOrThrow(warehouseId))
                : warehouseService.primaryLowStockThreshold();
        java.util.Map<Long, Integer> reservedByProduct = stockReservationService.reservedByProduct();
        java.util.Map<Long, Integer> inTransitByProduct = new java.util.HashMap<>();
        for (Object[] row : stockTransferRepository.inTransitByProduct()) {
            inTransitByProduct.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        List<StockValuationRow> rows = new ArrayList<>();
        for (Product product : productRepository.findAll()) {
            if (!product.isActive()) continue;
            Category category = product.getCategory();
            if (categoryId != null && (category == null || !categoryId.equals(category.getId()))) {
                continue;
            }
            int quantity = scopedQuantities != null ? scopedQuantities.getOrDefault(product.getId(), 0) : product.getStockQuantity();
            BigDecimal value = product.getCostPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            rows.add(new StockValuationRow(
                    product.getId(), product.getSku(), product.getName(),
                    category != null ? category.getName() : "Uncategorized",
                    quantity, product.getCostPrice(), value,
                    quantity < threshold ? "Low stock" : "OK",
                    reservedByProduct.getOrDefault(product.getId(), 0),
                    inTransitByProduct.getOrDefault(product.getId(), 0)));
        }
        rows.sort(Comparator.comparing(StockValuationRow::stockValue).reversed());
        return rows;
    }

    /**
     * Stock aging: every active, stock-tracked product that currently has sellable stock, with when it was
     * last received, when it last sold (a POS, sales-order or restaurant sale, or a restaurant ingredient
     * use) and how long ago, bucketed 0-30 / 31-60 / 61-90 / 91-180 / over 180 days or "Never sold".
     * Quantity is sellable stock across the chosen warehouse, branch or (default) every warehouse.
     * {@code deadStockDays} non-null keeps only dead stock: nothing sold for at least that many days
     * (or never) and nothing received within that window (so brand-new stock isn't flagged).
     */
    @Transactional(readOnly = true)
    public List<StockAgingRow> stockAging(Long categoryId, Long warehouseId, Long branchId, Integer deadStockDays) {
        boolean branchMode = branchId != null && warehouseId == null;
        java.util.Map<Long, Integer> quantities = branchMode
                ? warehouseService.sellableQuantitiesByProductForBranch(branchId)
                : warehouseService.sellableQuantitiesByProduct(warehouseId);
        java.util.Map<Long, Instant> lastReceived = new java.util.HashMap<>();
        java.util.Map<Long, Instant> lastSold = new java.util.HashMap<>();
        java.util.Set<String> saleTypes = java.util.Set.of("POS sale", "Sale order fulfillment", "Restaurant sale", "Restaurant ingredient consumption");
        java.util.Set<String> receiptTypes = java.util.Set.of("Purchase receipt", "Manufacturing output");
        for (StockMovementRow m : stockMovementHistory(Instant.EPOCH, Instant.now().plusSeconds(86_400), categoryId, null, warehouseId, branchMode ? branchId : null)) {
            if (m.productId() == null || m.occurredAt() == null) continue;
            if (saleTypes.contains(m.type())) {
                lastSold.merge(m.productId(), m.occurredAt(), (a, b) -> a.isAfter(b) ? a : b);
            } else if (receiptTypes.contains(m.type())) {
                lastReceived.merge(m.productId(), m.occurredAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        List<StockAgingRow> rows = new ArrayList<>();
        for (Product product : productRepository.findAll()) {
            if (!product.isActive() || !product.isTrackStock() || product.isService()) continue;
            Category category = product.getCategory();
            if (categoryId != null && (category == null || !categoryId.equals(category.getId()))) continue;
            int quantity = quantities.getOrDefault(product.getId(), 0);
            if (quantity <= 0) continue;
            Instant received = lastReceived.get(product.getId());
            Instant sold = lastSold.get(product.getId());
            LocalDate receivedDate = received != null ? received.atZone(zone).toLocalDate() : null;
            LocalDate soldDate = sold != null ? sold.atZone(zone).toLocalDate() : null;
            Long daysSinceSale = soldDate != null ? java.time.temporal.ChronoUnit.DAYS.between(soldDate, today) : null;
            if (deadStockDays != null) {
                if (daysSinceSale != null && daysSinceSale < deadStockDays) continue;
                if (receivedDate != null && java.time.temporal.ChronoUnit.DAYS.between(receivedDate, today) < deadStockDays) continue;
            }
            String bucket = daysSinceSale == null ? "Never sold"
                    : daysSinceSale <= 30 ? "0-30 days"
                    : daysSinceSale <= 60 ? "31-60 days"
                    : daysSinceSale <= 90 ? "61-90 days"
                    : daysSinceSale <= 180 ? "91-180 days" : "Over 180 days";
            BigDecimal cost = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
            rows.add(new StockAgingRow(product.getId(), product.getSku(), product.getName(),
                    category != null ? category.getName() : "Uncategorized",
                    quantity, cost, cost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP),
                    receivedDate != null ? receivedDate.toString() : null,
                    soldDate != null ? soldDate.toString() : null,
                    daysSinceSale != null ? String.valueOf(daysSinceSale) : "Never",
                    bucket));
        }
        // Oldest first: never sold, then the longest since a sale; ties by value.
        rows.sort(Comparator.comparing((StockAgingRow r) -> r.lastSold() == null ? "" : r.lastSold())
                .thenComparing(StockAgingRow::stockValue, Comparator.reverseOrder()));
        return rows;
    }

    /**
     * Task 1: "Inventory Report" — "Stock movement history" half. Task 6: "Any transaction
     * affecting stock should be shown to Stock Movement History (Pos sales, Sale orders, Purchases
     * orders)" widened this from two sources to seven structured, dated sources of stock-changing
     * events; the Manufacturing Module ("Add Manufacturing Module") added two more — a
     * Manufacturing Order run's own finished-good output and each component it consumed — for nine
     * total; the Restaurant module ("Restaurant should impact Inventory Module", then "Restaurants
     * need a dedicated wastage module") added three more again — a checked-out order's own sale,
     * its recipe-backed ingredient consumption, and now wastage — for twelve total. See {@link
     * StockMovementRow}'s javadoc for the full list and for why an earlier version of this
     * method's own javadoc claiming sales/returns never touch stock was wrong.
     * {@code categoryId} filters on each event's product's category,
     * same as every other per-product report here; an event whose line had no product (nothing to
     * snapshot stock against) or predates its type's snapshot fields is simply skipped, the same
     * way a null-category event already falls back to "Uncategorized" rather than being dropped.
     * {@code productId} (Fourier: "can we have Stock Movement History for a specific product")
     * narrows to one product's own history — the two filters combine (though picking both a
     * product and a different category it isn't in just yields an empty report, same as any other
     * over-narrow filter combination would). {@code warehouseId} (null = every warehouse) keeps
     * only rows that took stock from / put stock into that warehouse; each source records its
     * warehouse (directly, via its location, or via a snapshot id) and a row with none belongs to
     * the Main warehouse.
     */
    @Transactional(readOnly = true)
    public List<StockMovementRow> stockMovementHistory(Instant from, Instant to, Long categoryId, Long productId, Long warehouseId, Long branchId) {
        List<StockMovementRow> rows = new ArrayList<>();
        java.util.Map<Long, String> warehouseNames = new java.util.HashMap<>();
        for (com.posapp.warehouse.dto.WarehouseDto w : warehouseService.listAll()) {
            warehouseNames.put(w.id(), w.name());
        }
        // Branch filter: keep only rows from warehouses that belong to the branch (a warehouse with no branch is head office's).
        java.util.Set<Long> branchWarehouseIds = null;
        if (branchId != null) {
            Long headquartersId = branchService.headquartersId();
            branchWarehouseIds = new java.util.HashSet<>();
            for (com.posapp.warehouse.dto.WarehouseDto w : warehouseService.listAll()) {
                if (branchMatches(w.branchId(), branchId, headquartersId)) {
                    branchWarehouseIds.add(w.id());
                }
            }
        }
        MovementFilter filter = new MovementFilter(warehouseId, warehouseService.getDefaultWarehouseOrThrow().getId(), warehouseNames, branchWarehouseIds);
        for (StockAdjustment adjustment : stockAdjustmentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            addMovementRow(filter, warehouseIdOf(adjustment.getLocation()), rows, adjustment.getCreatedAt(), "Adjustment", adjustment.getProduct(), adjustment.getProductName(), categoryId, productId,
                    adjustment.getQuantityDelta(), adjustment.getStockBefore(), adjustment.getStockAfter(),
                    adjustment.getReason().name(),
                    adjustment.getLocationName() == null ? adjustment.getNote()
                            : "[" + (adjustment.getWarehouseName() != null ? adjustment.getWarehouseName() + " / " : "") + adjustment.getLocationName() + "]"
                                    + (adjustment.getNote() != null && !adjustment.getNote().isBlank() ? " " + adjustment.getNote() : ""));
        }
        for (StockReceipt receipt : stockReceiptRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            addMovementRow(filter, receivingWarehouseIdOf(receipt.getPurchaseOrder()), rows, receipt.getCreatedAt(), "Purchase receipt", receipt.getProduct(), receipt.getProductName(), categoryId, productId,
                    receipt.getQuantityReceived(), receipt.getStockBefore(), receipt.getStockAfter(),
                    receipt.getOrderNumber(), null);
        }
        // POS sale — OrderService.completeCheckout decrements stock per line; skip any line with
        // no snapshot (no product, or rung before OrderLine.stockBefore/stockAfter existed).
        for (SaleOrder order : completedOrders(from, to, null, null)) {
            for (OrderLine line : order.getLines()) {
                if (line.getStockBefore() == null || line.getStockAfter() == null) continue;
                addMovementRow(filter, order.getStockWarehouseId(), rows, order.getCompletedAt(), "POS sale", line.getProduct(), line.getDescription(), categoryId, productId,
                        -line.getQuantity(), line.getStockBefore(), line.getStockAfter(),
                        order.getInvoiceNumber(), null);
            }
        }
        // POS return — ReturnsService.processReturn increments stock back per line.
        for (SalesReturn salesReturn : salesReturnRepository.findByReturnDateBetweenOrderByReturnDateDesc(from, to)) {
            for (SalesReturnLine line : salesReturn.getLines()) {
                if (line.getStockBefore() == null || line.getStockAfter() == null) continue;
                Product product = line.getOrderLine() != null ? line.getOrderLine().getProduct() : null;
                String productName = line.getOrderLine() != null ? line.getOrderLine().getDescription() : null;
                addMovementRow(filter, salesReturn.getStockWarehouseId(), rows, salesReturn.getReturnDate(), "POS return", product, productName, categoryId, productId,
                        line.getQuantity(), line.getStockBefore(), line.getStockAfter(),
                        salesReturn.getReturnNumber(), salesReturn.getReason());
            }
        }
        // Sale order fulfillment — SalesOrderService.fulfillLines decrements stock; own structured event, same shape as StockReceipt.
        for (SalesOrderFulfillment fulfillment : salesOrderFulfillmentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            addMovementRow(filter, warehouseIdOf(fulfillment.getLocation()), rows, fulfillment.getCreatedAt(), "Sale order fulfillment", fulfillment.getProduct(), fulfillment.getProductName(), categoryId, productId,
                    -fulfillment.getQuantityFulfilled(), fulfillment.getStockBefore(), fulfillment.getStockAfter(),
                    fulfillment.getOrderNumber(),
                    fulfillment.getLocationName() == null ? null
                            : "[" + (fulfillment.getWarehouseName() != null ? fulfillment.getWarehouseName() + " / " : "") + fulfillment.getLocationName() + "]");
        }
        // Sale order return — SalesOrderReturnService.processReturn increments stock back per line. Task 3.
        for (SalesOrderReturn salesOrderReturn : salesOrderReturnRepository.findByReturnDateBetweenOrderByReturnDateDesc(from, to)) {
            for (SalesOrderReturnLine line : salesOrderReturn.getLines()) {
                if (line.getStockBefore() == null || line.getStockAfter() == null) continue;
                Product product = line.getOrderLine() != null ? line.getOrderLine().getProduct() : null;
                String productName = line.getOrderLine() != null ? line.getOrderLine().getProductName() : null;
                addMovementRow(filter, warehouseIdOf(salesOrderReturn.getLocation()), rows, salesOrderReturn.getReturnDate(), "Sale order return", product, productName, categoryId, productId,
                        line.getQuantity(), line.getStockBefore(), line.getStockAfter(),
                        salesOrderReturn.getReturnNumber(),
                        salesOrderReturn.getLocationName() == null ? salesOrderReturn.getReason()
                                : "[" + (salesOrderReturn.getWarehouseName() != null ? salesOrderReturn.getWarehouseName() + " / " : "") + salesOrderReturn.getLocationName() + "]"
                                        + (salesOrderReturn.getReason() != null && !salesOrderReturn.getReason().isBlank() ? " " + salesOrderReturn.getReason() : ""));
            }
        }
        // Purchase return — PurchaseReturnService.processReturn decrements stock (leaving the store back to the supplier). Task 4.
        for (PurchaseReturn purchaseReturn : purchaseReturnRepository.findByReturnDateBetweenOrderByReturnDateDesc(from, to)) {
            for (PurchaseReturnLine line : purchaseReturn.getLines()) {
                if (line.getStockBefore() == null || line.getStockAfter() == null) continue;
                Product product = line.getOrderLine() != null ? line.getOrderLine().getProduct() : null;
                String productName = line.getOrderLine() != null ? line.getOrderLine().getProductName() : null;
                addMovementRow(filter, receivingWarehouseIdOf(purchaseReturn.getPurchaseOrder()), rows, purchaseReturn.getReturnDate(), "Purchase return", product, productName, categoryId, productId,
                        -line.getQuantity(), line.getStockBefore(), line.getStockAfter(),
                        purchaseReturn.getReturnNumber(), purchaseReturn.getReason());
            }
        }
        // Manufacturing Module: one run produces a finished good ("Manufacturing output") and
        // consumes zero or more components ("Manufacturing consumption") — ManufacturingOrderService
        // .produce skips the snapshot entirely for a non-tracked product/component, same "no
        // snapshot, no row" treatment every other source here already follows.
        for (ManufacturingOrderRun run : manufacturingOrderRunRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            String orderNumber = run.getManufacturingOrder().getOrderNumber();
            String locationTag = locationTag(run.getManufacturingOrder().getWarehouseName(), run.getManufacturingOrder().getLocationName(), null);
            if (run.getStockBefore() != null && run.getStockAfter() != null) {
                Product product = run.getManufacturingOrder().getProduct();
                addMovementRow(filter, warehouseIdOf(run.getManufacturingOrder().getLocation()), rows, run.getCreatedAt(), "Manufacturing output", product, product.getName(), categoryId, productId,
                        run.getQuantityProduced(), run.getStockBefore(), run.getStockAfter(),
                        orderNumber, locationTag);
            }
            for (com.posapp.manufacturing.ManufacturingOrderConsumption consumption : run.getConsumptions()) {
                if (consumption.getStockBefore() == null || consumption.getStockAfter() == null) continue;
                addMovementRow(filter, warehouseIdOf(run.getManufacturingOrder().getLocation()), rows, run.getCreatedAt(), "Manufacturing consumption", consumption.getComponent(), consumption.getComponentName(), categoryId, productId,
                        -consumption.getQuantityConsumed(), consumption.getStockBefore(), consumption.getStockAfter(),
                        orderNumber, locationTag);
            }
        }
        // Restaurant module (Fourier: "Restaurant should impact Inventory Module"): a checked-out
        // order's own non-recipe lines snapshot their product's stock directly ("Restaurant sale"),
        // while a recipe-backed line's ingredients are snapshotted onto RestaurantOrderConsumption
        // instead ("Restaurant ingredient consumption") — see RestaurantOrderService#checkout. Same
        // "no snapshot, no row" guard as every other source above, since a non-stock-tracked
        // product/ingredient never gets one.
        for (RestaurantOrder order : restaurantOrderRepository.findByStatusAndClosedAtBetweenOrderByClosedAtDesc(RestaurantOrderStatus.CLOSED, from, to)) {
            String ticketNumber = order.getTicketNumber();
            for (RestaurantOrderLine line : order.getLines()) {
                if (line.getStockBefore() == null || line.getStockAfter() == null) continue;
                addMovementRow(filter, order.getStockWarehouseId(), rows, order.getClosedAt(), "Restaurant sale", line.getProduct(), line.getDescription(), categoryId, productId,
                        -line.getQuantity(), line.getStockBefore(), line.getStockAfter(),
                        ticketNumber, null);
            }
            for (RestaurantOrderConsumption consumption : order.getIngredientConsumptions()) {
                if (consumption.getStockBefore() == null || consumption.getStockAfter() == null) continue;
                addMovementRow(filter, order.getStockWarehouseId(), rows, order.getClosedAt(), "Restaurant ingredient consumption", consumption.getComponent(), consumption.getComponentName(), categoryId, productId,
                        -consumption.getQuantityConsumed(), consumption.getStockBefore(), consumption.getStockAfter(),
                        ticketNumber, null);
            }
        }
        // Fourier: "Restaurant wastage should be confirmed by admin once confirmed inventory and
        // account will be impacted" — only a CONFIRMED entry ever touched real stock (a
        // PENDING/REJECTED one never did), so this is filtered to that status and ranged by
        // confirmedAt (when the stock actually moved), not createdAt (when it was first logged) —
        // see RestaurantWastage's own javadoc. The null guard below is now mostly redundant with
        // that status filter (a CONFIRMED, stock-tracked entry always has both fields set) but is
        // kept for the same "no snapshot, no row" safety every other source above has.
        for (RestaurantWastage wastage : restaurantWastageRepository.findByStatusAndConfirmedAtBetweenOrderByConfirmedAtDesc(
                RestaurantWastageStatus.CONFIRMED, from, to)) {
            if (wastage.getStockBefore() == null || wastage.getStockAfter() == null) continue;
            addMovementRow(filter, wastage.getStockWarehouseId(), rows, wastage.getConfirmedAt(), "Restaurant wastage", wastage.getProduct(), wastage.getProductName(), categoryId, productId,
                    -wastage.getQuantity(), wastage.getStockBefore(), wastage.getStockAfter(),
                    wastage.getReason() != null ? wastage.getReason().name() : null, wastage.getNote());
        }
        // Packaging Module (Fourier: "Breakdown should visible into Stock movement"): one executed
        // PackagingBreakdown moves stock on two sides — decrements the parent ("Packaging breakdown
        // (parent)") and increments the child ("Packaging breakdown (child)") — same two-rows-per-event
        // shape Manufacturing's own output/consumption split already uses above. Same "no snapshot, no
        // row" guard as every other source above: a side that wasn't stock-tracked at breakdown time
        // has a null stockBefore/stockAfter and is simply skipped.
        for (PackagingBreakdown breakdown : packagingBreakdownRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            if (breakdown.getParentStockBefore() != null && breakdown.getParentStockAfter() != null) {
                addMovementRow(filter, warehouseIdOf(breakdown.getLocation()), rows, breakdown.getCreatedAt(), "Packaging breakdown (parent)", breakdown.getParentProduct(), breakdown.getParentProductName(), categoryId, productId,
                        -breakdown.getParentQuantity(), breakdown.getParentStockBefore(), breakdown.getParentStockAfter(),
                        breakdown.getChildProductName(), locationTag(breakdown.getWarehouseName(), breakdown.getLocationName(), breakdown.getNote()));
            }
            if (breakdown.getChildStockBefore() != null && breakdown.getChildStockAfter() != null) {
                addMovementRow(filter, warehouseIdOf(breakdown.getLocation()), rows, breakdown.getCreatedAt(), "Packaging breakdown (child)", breakdown.getChildProduct(), breakdown.getChildProductName(), categoryId, productId,
                        breakdown.getChildQuantityProduced(), breakdown.getChildStockBefore(), breakdown.getChildStockAfter(),
                        breakdown.getParentProductName(), locationTag(breakdown.getWarehouseName(), breakdown.getLocationName(), breakdown.getNote()));
            }
        }
        // Stock transfers: a same-branch transfer moves stock on confirmation (out of the source, into the
        // destination); an inter-branch one leaves on dispatch and arrives on receipt (or comes back when
        // returned to the sender). Transfers made before balances were recorded show with 0 -> 0 balances.
        for (com.posapp.warehouse.StockTransfer t : stockTransferRepository.findByStatusIn(java.util.List.of(
                com.posapp.warehouse.StockTransferStatus.CONFIRMED, com.posapp.warehouse.StockTransferStatus.IN_TRANSIT,
                com.posapp.warehouse.StockTransferStatus.RECEIVED, com.posapp.warehouse.StockTransferStatus.REJECTED))) {
            Product product = t.getProduct();
            String route = "[" + t.getFromWarehouseName() + " / " + t.getFromLocationName() + " \u2192 " + t.getToWarehouseName() + " / " + t.getToLocationName() + "]"
                    + (t.getNote() != null && !t.getNote().isBlank() ? " " + t.getNote() : "");
            String reference = "Transfer #" + t.getId();
            boolean interBranch = t.getDispatchedAt() != null;
            Instant leftAt = interBranch ? t.getDispatchedAt() : t.getConfirmedAt();
            boolean left = t.getStatus() != com.posapp.warehouse.StockTransferStatus.REJECTED || interBranch;
            if (left && leftAt != null && !leftAt.isBefore(from) && !leftAt.isAfter(to)) {
                addMovementRow(filter, warehouseIdOf(t.getFromLocation()), rows, leftAt, interBranch ? "Stock transfer out (dispatched)" : "Stock transfer out",
                        product, t.getProductName(), categoryId, productId, -t.getQuantity(),
                        t.getFromStockBefore() != null ? t.getFromStockBefore() : 0, t.getFromStockAfter() != null ? t.getFromStockAfter() : 0, reference, route);
            }
            if (t.getStatus() == com.posapp.warehouse.StockTransferStatus.CONFIRMED && t.getConfirmedAt() != null
                    && !t.getConfirmedAt().isBefore(from) && !t.getConfirmedAt().isAfter(to)) {
                addMovementRow(filter, warehouseIdOf(t.getToLocation()), rows, t.getConfirmedAt(), "Stock transfer in",
                        product, t.getProductName(), categoryId, productId, t.getQuantity(),
                        t.getToStockBefore() != null ? t.getToStockBefore() : 0, t.getToStockAfter() != null ? t.getToStockAfter() : 0, reference, route);
            }
            if (t.getStatus() == com.posapp.warehouse.StockTransferStatus.RECEIVED && t.getReceivedAt() != null
                    && t.getReceivedQuantity() != null && t.getReceivedQuantity() > 0
                    && !t.getReceivedAt().isBefore(from) && !t.getReceivedAt().isAfter(to)) {
                addMovementRow(filter, warehouseIdOf(t.getToLocation()), rows, t.getReceivedAt(), "Stock transfer in (received)",
                        product, t.getProductName(), categoryId, productId, t.getReceivedQuantity(),
                        t.getToStockBefore() != null ? t.getToStockBefore() : 0, t.getToStockAfter() != null ? t.getToStockAfter() : 0, reference, route);
            }
            if (t.getStatus() == com.posapp.warehouse.StockTransferStatus.REJECTED && interBranch && t.getUpdatedAt() != null
                    && !t.getUpdatedAt().isBefore(from) && !t.getUpdatedAt().isAfter(to)) {
                addMovementRow(filter, warehouseIdOf(t.getFromLocation()), rows, t.getUpdatedAt(), "Stock transfer returned to sender",
                        product, t.getProductName(), categoryId, productId, t.getQuantity(),
                        t.getReturnStockBefore() != null ? t.getReturnStockBefore() : 0, t.getReturnStockAfter() != null ? t.getReturnStockAfter() : 0, reference, route);
            }
        }
        rows.sort(Comparator.comparing(StockMovementRow::occurredAt).reversed());
        return rows;
    }

    /** Multi-warehouse phases 3-4: "[Warehouse / Location] note" for a movement row whose source recorded a location; the plain note (possibly null) when it didn't. */
    private static String locationTag(String warehouseName, String locationName, String note) {
        if (locationName == null) {
            return note;
        }
        String tag = "[" + (warehouseName != null ? warehouseName + " / " : "") + locationName + "]";
        return note != null && !note.isBlank() ? tag + " " + note : tag;
    }

    /**
     * "Restaurant Wastage" report — every recorded loss in the date range, any reason, any status
     * (PENDING/CONFIRMED/REJECTED — Fourier: "should be confirmed by admin"), whether or not the
     * product was stock-tracked (unlike {@link #stockMovementHistory} above, which can only show a
     * CONFIRMED, stock-tracked one — this is the complete wastage log including entries still
     * awaiting review or rejected outright, {@link #stockMovementHistory}'s "Restaurant wastage"
     * rows are the confirmed-and-stock-impacting subset of it). Ranged by {@code createdAt} (when
     * logged) since a PENDING/REJECTED entry has no {@code confirmedAt} at all.
     */
    @Transactional(readOnly = true)
    public List<RestaurantWastageReportRow> restaurantWastage(Instant from, Instant to, Long branchId) {
        List<RestaurantWastageReportRow> rows = new ArrayList<>();
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        for (RestaurantWastage wastage : restaurantWastageRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            if (branchId != null && !branchMatches(wastage.getBranchId(), branchId, headquartersId)) continue;
            Product product = wastage.getProduct();
            rows.add(new RestaurantWastageReportRow(
                    wastage.getCreatedAt(), product != null ? product.getSku() : null, wastage.getProductName(),
                    wastage.getQuantity(), wastage.getReason() != null ? wastage.getReason().name() : null, wastage.getNote(),
                    wastage.getStatus() != null ? wastage.getStatus().name() : null,
                    wastage.getStockBefore(), wastage.getStockAfter(), wastage.getCostImpact()));
        }
        return rows;
    }

    /** Shared row-builder for {@link #stockMovementHistory}'s twelve sources — applies the category and product filters once instead of twelve times each. */
    private void addMovementRow(MovementFilter filter, Long rowWarehouseId, List<StockMovementRow> rows, Instant occurredAt, String type, Product product, String productName,
                                 Long categoryId, Long productId, int quantityDelta, int stockBefore, int stockAfter, String reference, String note) {
        if (productId != null && (product == null || !productId.equals(product.getId()))) {
            return;
        }
        Category category = product != null ? product.getCategory() : null;
        if (categoryId != null && (category == null || !categoryId.equals(category.getId()))) {
            return;
        }
        // Warehouse filter: a row that recorded no warehouse (null) is the Main warehouse's — that is
        // also every row created before any source recorded one.
        Long effectiveWarehouseId = rowWarehouseId != null ? rowWarehouseId : filter.primaryId();
        if (filter.warehouseId() != null && !filter.warehouseId().equals(effectiveWarehouseId)) {
            return;
        }
        if (filter.branchWarehouseIds() != null && !filter.branchWarehouseIds().contains(effectiveWarehouseId)) {
            return;
        }
        rows.add(new StockMovementRow(
                occurredAt, type, product != null ? product.getSku() : null, productName,
                category != null ? category.getName() : "Uncategorized",
                quantityDelta, stockBefore, stockAfter, reference, note,
                filter.names().get(effectiveWarehouseId),
                product != null ? product.getId() : null));
    }

    /** Warehouse filter context for {@link #stockMovementHistory}: the requested warehouse (null = all), the Main warehouse's id, and id -> name for the Warehouse column. */
    private record MovementFilter(Long warehouseId, Long primaryId, java.util.Map<Long, String> names,
                                  /** Warehouses of the requested branch; null = no branch filter. */
                                  java.util.Set<Long> branchWarehouseIds) {
    }

    private static Long warehouseIdOf(com.posapp.warehouse.WarehouseLocation location) {
        return location != null && location.getWarehouse() != null ? location.getWarehouse().getId() : null;
    }

    private static Long receivingWarehouseIdOf(PurchaseOrder po) {
        return po != null && po.getReceivingWarehouse() != null ? po.getReceivingWarehouse().getId() : null;
    }

    /**
     * Task 4: "Add Purchase order reports." Every purchase order placed in the date range
     * (by {@code orderDate}), any status/supplier — a plain flat REPORT_DEFS-style listing (see
     * ReportController's endpoint), unlike Supplier Statement below which is scoped to one supplier.
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderReportRow> purchaseOrders(Instant from, Instant to, Long branchId) {
        List<PurchaseOrderReportRow> rows = new ArrayList<>();
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        for (PurchaseOrder po : purchaseOrderRepository.findByOrderDateBetweenOrderByOrderDateDesc(from, to)) {
            if (branchId != null && !branchMatches(po.getBranchId(), branchId, headquartersId)) continue;
            rows.add(purchaseOrderRow(po, true));
        }
        return rows;
    }

    /**
     * Task 5: "Add Supplier Statement" — every purchase order ever placed with one supplier, in
     * the chosen date range, oldest first (same "chronological ledger for one party" shape as
     * {@link #customerStatement}). 404s on an unknown supplier id, same convention as every other
     * id-in-the-path lookup in this codebase.
     */
    @Transactional(readOnly = true)
    public List<SupplierStatementRow> supplierStatement(Long supplierId, Instant from, Instant to) {
        if (!supplierRepository.existsById(supplierId)) {
            throw ApiException.notFound("Supplier not found: " + supplierId);
        }
        List<SupplierStatementRow> rows = new ArrayList<>();
        for (PurchaseOrder po : purchaseOrderRepository.findBySupplierIdAndOrderDateBetweenOrderByOrderDateAsc(supplierId, from, to)) {
            PurchaseOrderReportRow r = purchaseOrderRow(po, false);
            rows.add(new SupplierStatementRow(r.orderId(), r.orderNumber(), r.status(), r.orderDate(), r.expectedDate(),
                    r.lineCount(), r.quantityOrdered(), r.quantityReceived(), r.totalCost(), r.amountPaid(), r.amountOutstanding()));
        }
        return rows;
    }

    /**
     * Task: "Add Supplier payment" — snapshot, no date range: one row per supplier who currently
     * owes anything, the accounts-payable mirror of {@link #outstandingCredit}.
     */
    @Transactional(readOnly = true)
    public List<OutstandingPayableRow> outstandingPayables() {
        List<OutstandingPayableRow> rows = new ArrayList<>();
        for (Supplier supplier : supplierRepository.findAll()) {
            BigDecimal outstanding = supplierPaymentService.outstandingBalance(supplier.getId());
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;
            rows.add(new OutstandingPayableRow(supplier.getId(), supplier.getName(), supplier.getPhone(), outstanding));
        }
        rows.sort(Comparator.comparing(OutstandingPayableRow::outstandingBalance).reversed());
        return rows;
    }

    /** Task: "Add Sale order... Then let's have Sale as Menu" — every Sales Order placed in the date range, any status/customer. */
    @Transactional(readOnly = true)
    public List<SalesOrderReportRow> salesOrders(Instant from, Instant to, Long branchId) {
        List<SalesOrderReportRow> rows = new ArrayList<>();
        Long headquartersId = branchId != null ? branchService.headquartersId() : null;
        for (SalesOrder so : salesOrderRepository.findByOrderDateBetweenOrderByOrderDateDesc(from, to)) {
            if (branchId != null && !branchMatches(so.getBranchId(), branchId, headquartersId)) continue;
            Customer customer = so.getCustomer();
            int quantityOrdered = 0;
            int quantityFulfilled = 0;
            for (var line : so.getLines()) {
                quantityOrdered += line.getQuantityOrdered();
                quantityFulfilled += line.getQuantityFulfilled();
            }
            rows.add(new SalesOrderReportRow(
                    so.getId(), so.getOrderNumber(),
                    customer != null ? customer.getId() : null, customer != null ? customer.getName() : null,
                    so.getStatus().name(), so.getOrderDate(), so.getExpectedDate(), so.getDueDate(),
                    so.getLines().size(), quantityOrdered, quantityFulfilled,
                    SalesOrderService.totalAmount(so), customerCreditService.outstandingForSalesOrder(so)));
        }
        return rows;
    }

    // ---- Restaurant reports (Fourier: "Add all reports related to Restaurants Including all
    // type of orders etc") — every report below is built from RestaurantOrderRepository's own
    // closedAt-range query, the same "read directly, group in Java" convention as the POS Sales
    // reports above use OrderRepository.findCompletedForReport, just with no register concept
    // (restaurant orders aren't tied to one). "Sales"/"Profit" mean the same thing they do for the
    // POS reports (see this class's own javadoc) — revenue is post-discount, pre-tax
    // (RestaurantOrderCalculator.lineNetTotal), tax collected is never counted as revenue.

    /** "Restaurant Sales by Order Type" — every checked-out order, grouped by DINE_IN/TAKEAWAY/DELIVERY. */
    @Transactional(readOnly = true)
    public List<RestaurantOrderTypeSalesRow> restaurantOrderTypeSales(Instant from, Instant to, Long branchId) {
        record Acc(int[] orderCount, int[] qty, BigDecimal[] sales, BigDecimal[] tax) {}
        Map<String, Acc> byType = new LinkedHashMap<>();
        for (RestaurantOrder order : restaurantClosedOrders(from, to, branchId)) {
            String type = order.getOrderType().name();
            Acc acc = byType.computeIfAbsent(type, k -> new Acc(new int[]{0}, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
            acc.orderCount()[0]++;
            for (RestaurantOrderLine line : order.getLines()) {
                acc.qty()[0] += line.getQuantity();
                acc.sales()[0] = acc.sales()[0].add(RestaurantOrderCalculator.lineNetTotal(line));
                acc.tax()[0] = acc.tax()[0].add(RestaurantOrderCalculator.lineTax(line));
            }
        }
        List<RestaurantOrderTypeSalesRow> rows = new ArrayList<>();
        for (Map.Entry<String, Acc> e : byType.entrySet()) {
            Acc a = e.getValue();
            BigDecimal grand = a.sales()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new RestaurantOrderTypeSalesRow(e.getKey(), a.orderCount()[0], a.qty()[0], a.sales()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(RestaurantOrderTypeSalesRow::salesTotal).reversed());
        return rows;
    }

    /** "Restaurant Menu Item Sales" — every checked-out order's lines, grouped by menu item, across every order type. */
    @Transactional(readOnly = true)
    public List<RestaurantMenuItemSalesRow> restaurantMenuItemSales(Instant from, Instant to, Long branchId) {
        Map<Long, MenuItem> menuByProductId = menuItemsByProductId();
        record Acc(String sku, String name, String categoryName, int[] qty, BigDecimal[] sales, BigDecimal[] tax) {}
        Map<Long, Acc> byProduct = new LinkedHashMap<>();
        for (RestaurantOrder order : restaurantClosedOrders(from, to, branchId)) {
            for (RestaurantOrderLine line : order.getLines()) {
                Product product = line.getProduct();
                if (product == null) continue;
                MenuItem menuItem = menuByProductId.get(product.getId());
                String categoryName = menuItem != null && menuItem.getCategory() != null ? menuItem.getCategory().getName() : "Uncategorized";
                Acc acc = byProduct.computeIfAbsent(product.getId(), k -> new Acc(
                        product.getSku(), product.getName(), categoryName,
                        new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.sales()[0] = acc.sales()[0].add(RestaurantOrderCalculator.lineNetTotal(line));
                acc.tax()[0] = acc.tax()[0].add(RestaurantOrderCalculator.lineTax(line));
            }
        }
        List<RestaurantMenuItemSalesRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byProduct.entrySet()) {
            Acc a = e.getValue();
            BigDecimal grand = a.sales()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new RestaurantMenuItemSalesRow(e.getKey(), a.sku(), a.name(), a.categoryName(), a.qty()[0], a.sales()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(RestaurantMenuItemSalesRow::salesTotal).reversed());
        return rows;
    }

    /** "Restaurant Menu Category Sales" — the same grouping as {@link #restaurantMenuItemSales} one level up. */
    @Transactional(readOnly = true)
    public List<RestaurantMenuCategorySalesRow> restaurantMenuCategorySales(Instant from, Instant to, Long branchId) {
        Map<Long, MenuItem> menuByProductId = menuItemsByProductId();
        record Acc(String name, int[] qty, BigDecimal[] sales, BigDecimal[] tax) {}
        Map<Long, Acc> byCategory = new LinkedHashMap<>();
        for (RestaurantOrder order : restaurantClosedOrders(from, to, branchId)) {
            for (RestaurantOrderLine line : order.getLines()) {
                Product product = line.getProduct();
                if (product == null) continue;
                MenuItem menuItem = menuByProductId.get(product.getId());
                MenuCategory category = menuItem != null ? menuItem.getCategory() : null;
                Long key = category != null ? category.getId() : -1L;
                String name = category != null ? category.getName() : "Uncategorized";
                Acc acc = byCategory.computeIfAbsent(key, k -> new Acc(name, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.sales()[0] = acc.sales()[0].add(RestaurantOrderCalculator.lineNetTotal(line));
                acc.tax()[0] = acc.tax()[0].add(RestaurantOrderCalculator.lineTax(line));
            }
        }
        List<RestaurantMenuCategorySalesRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byCategory.entrySet()) {
            Acc a = e.getValue();
            BigDecimal grand = a.sales()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new RestaurantMenuCategorySalesRow(e.getKey() == -1L ? null : e.getKey(), a.name(), a.qty()[0], a.sales()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(RestaurantMenuCategorySalesRow::salesTotal).reversed());
        return rows;
    }

    /** "Restaurant Menu Item Profit" — see {@link RestaurantMenuItemProfitRow}'s own javadoc for how a recipe-backed item's real ingredient cost is used instead of its own (often unused) unitCost. */
    @Transactional(readOnly = true)
    public List<RestaurantMenuItemProfitRow> restaurantMenuItemProfit(Instant from, Instant to, Long branchId) {
        Map<Long, MenuItem> menuByProductId = menuItemsByProductId();
        record Acc(String sku, String name, String categoryName, int[] qty, BigDecimal[] revenue, BigDecimal[] cost) {}
        Map<Long, Acc> byProduct = new LinkedHashMap<>();
        for (RestaurantOrder order : restaurantClosedOrders(from, to, branchId)) {
            Map<Long, BigDecimal> ingredientCostByLineId = ingredientCostByLineId(order);
            for (RestaurantOrderLine line : order.getLines()) {
                Product product = line.getProduct();
                if (product == null) continue;
                MenuItem menuItem = menuByProductId.get(product.getId());
                String categoryName = menuItem != null && menuItem.getCategory() != null ? menuItem.getCategory().getName() : "Uncategorized";
                Acc acc = byProduct.computeIfAbsent(product.getId(), k -> new Acc(
                        product.getSku(), product.getName(), categoryName,
                        new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.revenue()[0] = acc.revenue()[0].add(RestaurantOrderCalculator.lineNetTotal(line));
                acc.cost()[0] = acc.cost()[0].add(lineTrueCost(line, ingredientCostByLineId));
            }
        }
        List<RestaurantMenuItemProfitRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byProduct.entrySet()) {
            Acc a = e.getValue();
            BigDecimal profit = a.revenue()[0].subtract(a.cost()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new RestaurantMenuItemProfitRow(e.getKey(), a.sku(), a.name(), a.categoryName(), a.qty()[0], a.revenue()[0], a.cost()[0], profit, marginPercent(profit, a.revenue()[0])));
        }
        rows.sort(Comparator.comparing(RestaurantMenuItemProfitRow::profit).reversed());
        return rows;
    }

    /** "Restaurant Menu Category Profit" — the same grouping as {@link #restaurantMenuItemProfit} one level up. */
    @Transactional(readOnly = true)
    public List<RestaurantMenuCategoryProfitRow> restaurantMenuCategoryProfit(Instant from, Instant to, Long branchId) {
        Map<Long, MenuItem> menuByProductId = menuItemsByProductId();
        record Acc(String name, int[] qty, BigDecimal[] revenue, BigDecimal[] cost) {}
        Map<Long, Acc> byCategory = new LinkedHashMap<>();
        for (RestaurantOrder order : restaurantClosedOrders(from, to, branchId)) {
            Map<Long, BigDecimal> ingredientCostByLineId = ingredientCostByLineId(order);
            for (RestaurantOrderLine line : order.getLines()) {
                Product product = line.getProduct();
                if (product == null) continue;
                MenuItem menuItem = menuByProductId.get(product.getId());
                MenuCategory category = menuItem != null ? menuItem.getCategory() : null;
                Long key = category != null ? category.getId() : -1L;
                String name = category != null ? category.getName() : "Uncategorized";
                Acc acc = byCategory.computeIfAbsent(key, k -> new Acc(name, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                acc.qty()[0] += line.getQuantity();
                acc.revenue()[0] = acc.revenue()[0].add(RestaurantOrderCalculator.lineNetTotal(line));
                acc.cost()[0] = acc.cost()[0].add(lineTrueCost(line, ingredientCostByLineId));
            }
        }
        List<RestaurantMenuCategoryProfitRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byCategory.entrySet()) {
            Acc a = e.getValue();
            BigDecimal profit = a.revenue()[0].subtract(a.cost()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new RestaurantMenuCategoryProfitRow(e.getKey() == -1L ? null : e.getKey(), a.name(), a.qty()[0], a.revenue()[0], a.cost()[0], profit, marginPercent(profit, a.revenue()[0])));
        }
        rows.sort(Comparator.comparing(RestaurantMenuCategoryProfitRow::profit).reversed());
        return rows;
    }

    /** "Restaurant Orders" (detailed transactions) — one row per checked-out order, any order type, the finest-grained restaurant sales report. */
    @Transactional(readOnly = true)
    public List<RestaurantOrderTransactionRow> restaurantOrderTransactions(Instant from, Instant to, Long branchId) {
        List<RestaurantOrderTransactionRow> rows = new ArrayList<>();
        for (RestaurantOrder order : restaurantClosedOrders(from, to, branchId)) {
            AppUser server = order.getServer();
            String methods = order.getPayments().stream().map(RestaurantPayment::getMethod)
                    .reduce((a, b) -> a + " + " + b).orElse("—");
            int itemCount = order.getLines().stream().mapToInt(RestaurantOrderLine::getQuantity).sum();
            rows.add(new RestaurantOrderTransactionRow(
                    order.getId(), order.getTicketNumber(), order.getOrderType().name(), order.displayLabel(),
                    server != null ? server.getFullName() : "—",
                    order.getOpenedAt(), order.getClosedAt(), itemCount,
                    RestaurantOrderCalculator.subtotal(order), RestaurantOrderCalculator.taxTotal(order), RestaurantOrderCalculator.total(order),
                    methods));
        }
        return rows;
    }

    /** "Restaurant Sales by Server" — every checked-out order, grouped by who was serving it. "Unassigned" for an order with no server ever set. */
    @Transactional(readOnly = true)
    public List<RestaurantServerSalesRow> restaurantServerSales(Instant from, Instant to, Long branchId) {
        record Acc(String name, int[] orderCount, int[] qty, BigDecimal[] sub, BigDecimal[] tax) {}
        Map<Long, Acc> byServer = new LinkedHashMap<>();
        for (RestaurantOrder order : restaurantClosedOrders(from, to, branchId)) {
            AppUser server = order.getServer();
            Long key = server != null ? server.getId() : -1L;
            String name = server != null ? server.getFullName() : "Unassigned";
            Acc acc = byServer.computeIfAbsent(key, k -> new Acc(name, new int[]{0}, new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
            acc.orderCount()[0]++;
            for (RestaurantOrderLine line : order.getLines()) {
                acc.qty()[0] += line.getQuantity();
                acc.sub()[0] = acc.sub()[0].add(RestaurantOrderCalculator.lineNetTotal(line));
                acc.tax()[0] = acc.tax()[0].add(RestaurantOrderCalculator.lineTax(line));
            }
        }
        List<RestaurantServerSalesRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Acc> e : byServer.entrySet()) {
            Acc a = e.getValue();
            BigDecimal grand = a.sub()[0].add(a.tax()[0]).setScale(2, RoundingMode.HALF_UP);
            rows.add(new RestaurantServerSalesRow(e.getKey() == -1L ? null : e.getKey(), a.name(), a.orderCount()[0], a.qty()[0], a.sub()[0], a.tax()[0], grand));
        }
        rows.sort(Comparator.comparing(RestaurantServerSalesRow::grandTotal).reversed());
        return rows;
    }

    /** "Restaurant Cancelled Orders" — every order cancelled (never checked out) in the date range, any order type — see {@link RestaurantCancelledOrderRow}'s own javadoc. */
    @Transactional(readOnly = true)
    public List<RestaurantCancelledOrderRow> restaurantCancelledOrders(Instant from, Instant to, Long branchId) {
        List<RestaurantCancelledOrderRow> rows = new ArrayList<>();
        for (RestaurantOrder order : restaurantOrderRepository.findByStatusAndClosedAtBetweenOrderByClosedAtDesc(RestaurantOrderStatus.CANCELLED, from, to)) {
            if (branchId != null && !branchMatches(order.getBranchId(), branchId, headquartersIdOrNull(branchId))) continue;
            AppUser server = order.getServer();
            int itemCount = order.getLines().stream().mapToInt(RestaurantOrderLine::getQuantity).sum();
            rows.add(new RestaurantCancelledOrderRow(
                    order.getId(), order.getOrderType().name(), order.displayLabel(),
                    server != null ? server.getFullName() : "—",
                    order.getOpenedAt(), order.getClosedAt(), itemCount,
                    RestaurantOrderCalculator.total(order)));
        }
        return rows;
    }

    /** Closed restaurant orders in the range; a branch keeps only the orders prepared from its kitchen (null branch = head office). */
    private List<RestaurantOrder> restaurantClosedOrders(Instant from, Instant to, Long branchId) {
        List<RestaurantOrder> orders = restaurantOrderRepository.findByStatusAndClosedAtBetweenOrderByClosedAtDesc(RestaurantOrderStatus.CLOSED, from, to);
        if (branchId == null) return orders;
        Long headquartersId = branchService.headquartersId();
        return orders.stream().filter(o -> branchMatches(o.getBranchId(), branchId, headquartersId)).toList();
    }

    private Long headquartersIdOrNull(Long branchId) {
        return branchId != null ? branchService.headquartersId() : null;
    }

    /** One {@link MenuItem} per product id (first one wins) — resolves a restaurant order line's menu category for the reports above. */
    private Map<Long, MenuItem> menuItemsByProductId() {
        Map<Long, MenuItem> map = new LinkedHashMap<>();
        for (MenuItem item : menuItemRepository.findAllByOrderBySortOrderAsc()) {
            if (item.getProduct() != null) {
                map.putIfAbsent(item.getProduct().getId(), item);
            }
        }
        return map;
    }

    /** Sum of {@code RestaurantOrderConsumption.unitCost × quantityConsumed} per line id, for the one order — see {@link #lineTrueCost}. */
    private Map<Long, BigDecimal> ingredientCostByLineId(RestaurantOrder order) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (RestaurantOrderConsumption consumption : order.getIngredientConsumptions()) {
            if (consumption.getLine() == null) continue;
            Long lineId = consumption.getLine().getId();
            BigDecimal add = consumption.getUnitCost().multiply(BigDecimal.valueOf(consumption.getQuantityConsumed()));
            map.merge(lineId, add, BigDecimal::add);
        }
        return map;
    }

    /**
     * A line's real cost of goods for profit reporting: a recipe-backed line's ingredient
     * consumption rows when it has any (see {@link #ingredientCostByLineId}), else the line's own
     * snapshotted {@code unitCost} × quantity ({@link RestaurantOrderCalculator#lineCost}) — same
     * branching {@code RestaurantOrderService#checkout} itself uses to compute the COGS it posts
     * to Accounting, so this report's "cost" figure always matches what was actually posted.
     */
    private BigDecimal lineTrueCost(RestaurantOrderLine line, Map<Long, BigDecimal> ingredientCostByLineId) {
        BigDecimal recipeCost = ingredientCostByLineId.get(line.getId());
        return recipeCost != null ? recipeCost.setScale(2, RoundingMode.HALF_UP) : RestaurantOrderCalculator.lineCost(line);
    }

    /** Shared by {@link #purchaseOrders} and {@link #supplierStatement} — {@code includeSupplier} just controls whether resolving the (LAZY) supplier name is worth doing (Supplier Statement already knows which supplier it is, from its own page header). */
    private PurchaseOrderReportRow purchaseOrderRow(PurchaseOrder po, boolean includeSupplier) {
        Supplier supplier = includeSupplier ? po.getSupplier() : null;
        int quantityOrdered = 0;
        int quantityReceived = 0;
        for (PurchaseOrderLine line : po.getLines()) {
            quantityOrdered += line.getQuantityOrdered();
            quantityReceived += line.getQuantityReceived();
        }
        // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
        // delegates to SupplierPaymentService.totalCost (now tax-inclusive) instead of
        // duplicating the sum(unitCost * quantityOrdered) formula here, so this report can never
        // drift from what the Purchase Order / Supplier Statement screens show as "the" cost.
        BigDecimal totalCost = SupplierPaymentService.totalCost(po);
        BigDecimal amountPaid = supplierPaymentService.amountPaidForOrder(po);
        BigDecimal amountOutstanding = supplierPaymentService.amountOwedForOrder(po);
        return new PurchaseOrderReportRow(
                po.getId(), po.getOrderNumber(),
                supplier != null ? supplier.getId() : null, supplier != null ? supplier.getName() : null,
                po.getStatus().name(), po.getOrderDate(), po.getExpectedDate(),
                po.getLines().size(), quantityOrdered, quantityReceived, totalCost.setScale(2, RoundingMode.HALF_UP),
                amountPaid, amountOutstanding);
    }

    private List<SaleOrder> completedOrders(Instant from, Instant to, Long registerId, Long branchId) {
        List<SaleOrder> orders = orderRepository.findCompletedForReport(OrderStatus.COMPLETED, from, to, registerId);
        if (branchId == null) {
            return orders;
        }
        Long headquartersId = branchService.headquartersId();
        return orders.stream().filter(o -> branchMatches(o.getBranchId(), branchId, headquartersId)).toList();
    }

    /** A record whose branch is null (made before branches existed) belongs to head office. */
    static boolean branchMatches(Long recordBranchId, Long filterBranchId, Long headquartersId) {
        Long effective = recordBranchId != null ? recordBranchId : headquartersId;
        return effective.equals(filterBranchId);
    }

    private static BigDecimal marginPercent(BigDecimal profit, BigDecimal revenue) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
    }
}
