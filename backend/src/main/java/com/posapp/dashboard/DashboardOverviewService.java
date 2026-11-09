package com.posapp.dashboard;

import com.posapp.bill.BillService;
import com.posapp.bill.dto.BillDto;
import com.posapp.branch.BranchService;
import com.posapp.catalog.CatalogService;
import com.posapp.collections.CollectionDtos.CustomersPage;
import com.posapp.collections.CollectionDtos.Summary;
import com.posapp.collections.CollectionService;
import com.posapp.common.ListPaging;
import com.posapp.dashboard.DashboardOverviewDtos.*;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderStatus;
import com.posapp.order.SaleOrder;
import com.posapp.register.RegisterSessionRepository;
import com.posapp.register.SessionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dashboard's action-oriented figures. Days are UTC days, the same simplification the existing "today" tiles use. Sales are shown as two separate series: till sales
 * (completed POS orders) and invoices issued (opening balances and voids excluded), because a business may use either or both and they must never be added up blindly.
 * Returns on the till are not netted off. Everything is read live on every call.
 */
@Service
public class DashboardOverviewService {

    private static final int DAYS = 14;
    private static final int TOP = 5;
    private static final int TOP_SELLER_DAYS = 7;

    private final OrderRepository orderRepository;
    private final BranchService branchService;
    private final CollectionService collectionService;
    private final BillService billService;
    private final CatalogService catalogService;
    private final RegisterSessionRepository sessionRepository;
    private final EntityManager em;

    public DashboardOverviewService(OrderRepository orderRepository, BranchService branchService, CollectionService collectionService, BillService billService,
                                    CatalogService catalogService, RegisterSessionRepository sessionRepository, EntityManager em) {
        this.orderRepository = orderRepository;
        this.branchService = branchService;
        this.collectionService = collectionService;
        this.billService = billService;
        this.catalogService = catalogService;
        this.sessionRepository = sessionRepository;
        this.em = em;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate dayOf(Instant i) {
        return i.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private long count(String jpql) {
        return ((Number) em.createQuery(jpql).getSingleResult()).longValue();
    }

    @Transactional
    public Overview overview(Long branchId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first = today.minusDays(DAYS - 1);
        Instant from = first.atStartOfDay(ZoneOffset.UTC).toInstant();

        // ---- till sales and invoices per day ----
        List<SaleOrder> orders = orderRepository.findByStatusAndCompletedAtGreaterThanEqual(OrderStatus.COMPLETED, from);
        if (branchId != null) {
            Long hq = branchService.headquartersId();
            orders = orders.stream().filter(o -> ListPaging.branch(o.getBranchId(), branchId, hq)).toList();
        }
        Map<LocalDate, BigDecimal> pos = new HashMap<>();
        Map<LocalDate, Integer> posCount = new HashMap<>();
        for (SaleOrder o : orders) {
            LocalDate d = dayOf(o.getCompletedAt());
            pos.merge(d, OrderCalculator.total(o), BigDecimal::add);
            posCount.merge(d, 1, Integer::sum);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> invoiceRows = em.createQuery("select i.invoiceDate, i.totalAmount, i.branchId from Invoice i where i.status = com.posapp.invoice.InvoiceStatus.ISSUED "
                + "and i.openingBalance = false and i.invoiceDate >= :from").setParameter("from", from).getResultList();
        Map<LocalDate, BigDecimal> inv = new HashMap<>();
        Map<LocalDate, Integer> invCount = new HashMap<>();
        Long hqId = branchId == null ? null : branchService.headquartersId();
        for (Object[] r : invoiceRows) {
            if (!ListPaging.branch((Long) r[2], branchId, hqId)) continue;
            LocalDate d = dayOf((Instant) r[0]);
            inv.merge(d, (BigDecimal) r[1], BigDecimal::add);
            invCount.merge(d, 1, Integer::sum);
        }
        List<DayPoint> days = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            LocalDate d = first.plusDays(i);
            days.add(new DayPoint(d, posCount.getOrDefault(d, 0), money(pos.get(d)), invCount.getOrDefault(d, 0), money(inv.get(d))));
        }
        Comparison comparison = comparison(days);

        // ---- who owes ----
        CustomersPage owingPage = collectionService.customers(false, false, branchId, null, 0, TOP);
        Summary s = owingPage.summary();
        List<Owing> top = owingPage.rows().stream().map(r -> new Owing(r.customerId(), r.name(), r.outstanding(), r.overdue(), r.oldestDaysOverdue())).toList();
        OwingBlock owing = new OwingBlock(s.totalOutstanding(), s.totalOverdue(), s.overdueItems(), s.customersOwing(), s.customersOverdue(), top);

        // ---- what we owe suppliers ----
        List<BillDto> unpaid = billService.listAll(null).stream()
                .filter(b -> "ISSUED".equals(b.status()) && b.amountOutstanding() != null && b.amountOutstanding().signum() > 0)
                .sorted(Comparator.comparing(BillDto::billDate)).toList();
        BigDecimal unpaidTotal = unpaid.stream().map(BillDto::amountOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<UnpaidBill> oldest = unpaid.stream().limit(TOP)
                .map(b -> new UnpaidBill(b.id(), b.billNumber(), b.supplierName(), money(b.amountOutstanding()), Math.max(0, ChronoUnit.DAYS.between(dayOf(b.billDate()), today)))).toList();
        BillsBlock bills = new BillsBlock(money(unpaidTotal), unpaid.size(), oldest);

        // ---- top sellers over the last week ----
        Instant weekFrom = today.minusDays(TOP_SELLER_DAYS - 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<Long, BigDecimal[]> qty = new LinkedHashMap<>();
        Map<Long, BigDecimal> revenue = new HashMap<>();
        Map<Long, String[]> names = new HashMap<>();
        for (SaleOrder o : orders) {
            if (o.getCompletedAt().isBefore(weekFrom)) continue;
            for (OrderLine l : o.getLines()) {
                if (l.getProduct() == null) continue;
                Long pid = l.getProduct().getId();
                qty.computeIfAbsent(pid, k -> new BigDecimal[]{BigDecimal.ZERO})[0] = qty.get(pid)[0].add(l.getQuantity());
                revenue.merge(pid, OrderCalculator.lineTotal(l), BigDecimal::add);
                names.putIfAbsent(pid, new String[]{l.getProduct().getName(), l.getProduct().getSku()});
            }
        }
        List<TopSeller> sellers = qty.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]))
                .limit(TOP)
                .map(e -> new TopSeller(e.getKey(), names.get(e.getKey())[0], names.get(e.getKey())[1], e.getValue()[0], money(revenue.get(e.getKey())))).toList();

        // ---- things that need attention ----
        var lowStock = catalogService.listLowStockPrimary();
        long outOfStock = lowStock.stream().filter(p -> p.stockQuantity().signum() <= 0).count();
        List<AttentionItem> attention = new ArrayList<>();
        attention.add(new AttentionItem("OVERDUE_INVOICES", "Overdue invoices", s.overdueItems(), money(s.totalOverdue())));
        attention.add(new AttentionItem("UNPAID_BILLS", "Bills to pay", unpaid.size(), money(unpaidTotal)));
        attention.add(new AttentionItem("OUT_OF_STOCK", "Out of stock", outOfStock, null));
        attention.add(new AttentionItem("LOW_STOCK", "Low on stock", lowStock.size() - outOfStock, null));
        attention.add(new AttentionItem("EXPENSES_TO_APPROVE", "Expenses to approve", count("select count(e) from Expense e where e.status = com.posapp.accounting.ExpenseStatus.PENDING"), null));
        attention.add(new AttentionItem("ADJUSTMENTS_TO_APPROVE", "Stock adjustments to approve",
                count("select count(a) from StockAdjustment a where a.status = com.posapp.inventory.StockAdjustmentStatus.PENDING"), null));
        attention.add(new AttentionItem("ORDERS_TO_DELIVER", "Sales orders to deliver",
                count("select count(o) from SalesOrder o where o.status in (com.posapp.salesorder.SalesOrderStatus.CONFIRMED, com.posapp.salesorder.SalesOrderStatus.PARTIALLY_FULFILLED)"), null));
        attention.add(new AttentionItem("PURCHASES_TO_RECEIVE", "Purchase orders to receive",
                count("select count(p) from PurchaseOrder p where p.status in (com.posapp.inventory.PurchaseOrderStatus.SENT, com.posapp.inventory.PurchaseOrderStatus.PARTIALLY_RECEIVED)"), null));
        attention.add(new AttentionItem("OPEN_SHIFTS", "Open shifts", sessionRepository.countByStatus(SessionStatus.OPEN), null));

        return new Overview(today, attention, days, comparison, owing, bills, sellers, TOP_SELLER_DAYS);
    }

    static Comparison comparison(List<DayPoint> days) {
        int n = days.size();
        DayPoint today = days.get(n - 1);
        DayPoint lastWeek = days.get(n - 8);
        BigDecimal last7 = BigDecimal.ZERO, previous7 = BigDecimal.ZERO;
        for (int i = 0; i < 7; i++) {
            last7 = last7.add(total(days.get(n - 1 - i)));
            previous7 = previous7.add(total(days.get(n - 8 - i)));
        }
        return new Comparison(money(total(today)), money(total(lastWeek)), money(last7), money(previous7), today.posCount() + today.invoiceCount(), lastWeek.posCount() + lastWeek.invoiceCount());
    }

    private static BigDecimal total(DayPoint d) {
        return d.pos().add(d.invoiced());
    }
}
