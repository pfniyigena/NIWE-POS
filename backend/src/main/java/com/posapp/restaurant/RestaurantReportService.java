package com.posapp.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.HotelClock;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.restaurant.dto.DayCloseRequest;
import com.posapp.restaurant.dto.DayCloseRowDto;
import com.posapp.restaurant.dto.EndOfDayDto;
import com.posapp.restaurant.dto.RestaurantAnalysisDto;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Sales analysis (hours, days, order types, payments, menu mix, servers, tables, controls) and the daily cash-up / Z-report.
 * Both read the same checked-out orders, so the two always agree. A sale belongs to the day (in the store's time zone) it was paid.
 */
@Service
public class RestaurantReportService {

    private static final int MAX_DAYS = 366;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final RestaurantOrderRepository orderRepository;
    private final RestaurantVoidLogRepository voidLogRepository;
    private final RestaurantCashEventRepository cashEventRepository;
    private final RestaurantDayCloseRepository closeRepository;
    private final MenuItemRepository menuItemRepository;
    private final PaymentMethodService paymentMethodService;
    private final HotelClock clock;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public RestaurantReportService(RestaurantOrderRepository orderRepository, RestaurantVoidLogRepository voidLogRepository,
                                   RestaurantCashEventRepository cashEventRepository, RestaurantDayCloseRepository closeRepository,
                                   MenuItemRepository menuItemRepository, PaymentMethodService paymentMethodService, HotelClock clock,
                                   AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.voidLogRepository = voidLogRepository;
        this.cashEventRepository = cashEventRepository;
        this.closeRepository = closeRepository;
        this.menuItemRepository = menuItemRepository;
        this.paymentMethodService = paymentMethodService;
        this.clock = clock;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal r2(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return BigDecimal.ZERO.setScale(1);
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal div(BigDecimal a, long n) {
        return n == 0 ? ZERO : a.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    private ZoneId zone() {
        return clock.zone();
    }

    private Instant start(LocalDate d) {
        return d.atStartOfDay(zone()).toInstant();
    }

    private Instant end(LocalDate d) {
        return d.plusDays(1).atStartOfDay(zone()).toInstant().minusMillis(1);
    }

    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate today = clock.today();
        LocalDate f = from != null ? from : (to != null ? to : today);
        LocalDate t = to != null ? to : (from != null ? today.isBefore(f) ? f : today : today);
        if (t.isBefore(f)) throw ApiException.badRequest("The end date is before the start date");
        if (Duration.between(f.atStartOfDay(), t.plusDays(1).atStartOfDay()).toDays() > MAX_DAYS) throw ApiException.badRequest("Choose a range of one year at most");
        return new LocalDate[]{f, t};
    }

    private List<RestaurantOrder> closedOrders(LocalDate f, LocalDate t) {
        return orderRepository.findByStatusAndClosedAtBetweenOrderByClosedAtDesc(RestaurantOrderStatus.CLOSED, start(f), end(t));
    }

    private static String typeOf(RestaurantOrder o) {
        return o.getRoomLabel() != null ? "ROOM_SERVICE" : o.getOrderType().name();
    }

    private static boolean dineInRoot(RestaurantOrder o) {
        return o.getParentOrderId() == null && o.getOrderType() == RestaurantOrderType.DINE_IN && o.getRoomLabel() == null;
    }

    private static int covers(RestaurantOrder o) {
        return dineInRoot(o) ? o.getGuestCount() : 0;
    }

    private static BigDecimal net(RestaurantOrder o) {
        return RestaurantOrderCalculator.subtotal(o);
    }

    private static BigDecimal billed(RestaurantOrder o) {
        return RestaurantOrderCalculator.total(o);
    }

    private static BigDecimal gross(RestaurantOrder o) {
        BigDecimal t = BigDecimal.ZERO;
        for (RestaurantOrderLine l : o.getLines()) t = t.add(RestaurantOrderCalculator.lineGross(l));
        return t;
    }

    private static BigDecimal tip(RestaurantOrder o) {
        return o.getTipAmount() == null ? BigDecimal.ZERO : o.getTipAmount();
    }

    private static BigDecimal deposit(RestaurantOrder o) {
        return o.getDepositApplied() == null ? BigDecimal.ZERO : o.getDepositApplied();
    }

    private static BigDecimal lineCost(RestaurantOrderLine l, Map<Long, BigDecimal> ingredientCost) {
        BigDecimal ing = ingredientCost.get(l.getId());
        return ing != null ? ing : RestaurantOrderCalculator.lineCost(l);
    }

    private static Map<Long, BigDecimal> ingredientCost(RestaurantOrder o) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (RestaurantOrderConsumption c : o.getIngredientConsumptions()) {
            if (c.getLine() == null) continue;
            map.merge(c.getLine().getId(), c.getUnitCost().multiply(c.getQuantityConsumed()), BigDecimal::add);
        }
        return map;
    }

    private static BigDecimal orderCost(RestaurantOrder o) {
        Map<Long, BigDecimal> ing = ingredientCost(o);
        BigDecimal t = BigDecimal.ZERO;
        for (RestaurantOrderLine l : o.getLines()) t = t.add(lineCost(l, ing));
        return t;
    }

    private static BigDecimal items(RestaurantOrder o) {
        return o.getLines().stream().map(RestaurantOrderLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String serverName(RestaurantOrder o) {
        AppUser s = o.getServer();
        return s == null ? "Unassigned" : (s.getFullName() != null && !s.getFullName().isBlank() ? s.getFullName() : s.getUsername());
    }

    /** For each root order: when its last check was paid, so a split table counts as one party. */
    private static Map<Long, Instant> partyEnd(List<RestaurantOrder> orders) {
        Map<Long, Instant> end = new LinkedHashMap<>();
        for (RestaurantOrder o : orders) {
            Long root = o.getParentOrderId() != null ? o.getParentOrderId() : o.getId();
            end.merge(root, o.getClosedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return end;
    }

    private static Map<Long, BigDecimal> partyNet(List<RestaurantOrder> orders) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        for (RestaurantOrder o : orders) m.merge(o.getParentOrderId() != null ? o.getParentOrderId() : o.getId(), net(o), BigDecimal::add);
        return m;
    }

    // ------------------------------------------------------------------ analysis

    @Transactional(readOnly = true)
    public RestaurantAnalysisDto analysis(LocalDate fromDate, LocalDate toDate) {
        LocalDate[] r = range(fromDate, toDate);
        LocalDate from = r[0], to = r[1];
        List<RestaurantOrder> orders = closedOrders(from, to);
        ZoneId zone = zone();

        // headline numbers
        int checks = orders.size(), parties = 0, covers = 0;
        BigDecimal itemsSold = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO, discounts = BigDecimal.ZERO, net = BigDecimal.ZERO, service = BigDecimal.ZERO, tax = BigDecimal.ZERO,
                rounding = BigDecimal.ZERO, billed = BigDecimal.ZERO, tips = BigDecimal.ZERO, cost = BigDecimal.ZERO, dineInNet = BigDecimal.ZERO;
        for (RestaurantOrder o : orders) {
            if (o.getParentOrderId() == null) parties++;
            covers += covers(o);
            itemsSold = itemsSold.add(items(o));
            gross = gross.add(gross(o));
            discounts = discounts.add(RestaurantOrderCalculator.discountTotal(o));
            net = net.add(net(o));
            service = service.add(RestaurantOrderCalculator.serviceCharge(o));
            tax = tax.add(RestaurantOrderCalculator.taxTotal(o));
            rounding = rounding.add(RestaurantOrderCalculator.roundingAdjustment(o));
            billed = billed.add(billed(o));
            tips = tips.add(tip(o));
            cost = cost.add(orderCost(o));
            if (o.getOrderType() == RestaurantOrderType.DINE_IN && o.getRoomLabel() == null) dineInNet = dineInNet.add(net(o));
        }
        Map<Long, Instant> partyEnd = partyEnd(orders);
        Map<Long, BigDecimal> partyNet = partyNet(orders);
        long minutesSum = 0;
        int minutesN = 0;
        for (RestaurantOrder o : orders) {
            if (!dineInRoot(o) || o.getTable() == null) continue;
            Instant e = partyEnd.getOrDefault(o.getId(), o.getClosedAt());
            minutesSum += Math.max(0, Duration.between(o.getOpenedAt(), e).toMinutes());
            minutesN++;
        }
        BigDecimal profit = net.subtract(cost);
        var totals = new RestaurantAnalysisDto.Totals(checks, parties, covers, itemsSold, r2(gross), r2(discounts), r2(net), r2(service), r2(tax), r2(rounding),
                r2(billed), r2(tips), div(net, checks), div(dineInNet, covers), r2(cost), r2(profit), pct(profit, net), minutesN == 0 ? 0 : (int) (minutesSum / minutesN));

        // hours
        int[][] hc = new int[24][2];
        BigDecimal[] hn = new BigDecimal[24];
        java.util.Arrays.fill(hn, BigDecimal.ZERO);
        for (RestaurantOrder o : orders) {
            int h = o.getClosedAt().atZone(zone).getHour();
            hc[h][0]++;
            hc[h][1] += covers(o);
            hn[h] = hn[h].add(net(o));
        }
        List<RestaurantAnalysisDto.Hour> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) hours.add(new RestaurantAnalysisDto.Hour(h, hc[h][0], hc[h][1], r2(hn[h])));

        // days and weekdays
        Map<LocalDate, int[]> dayCount = new TreeMap<>();
        Map<LocalDate, BigDecimal[]> dayMoney = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            dayCount.put(d, new int[2]);
            dayMoney.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (RestaurantOrder o : orders) {
            LocalDate d = o.getClosedAt().atZone(zone).toLocalDate();
            int[] c = dayCount.get(d);
            if (c == null) continue;
            c[0]++;
            c[1] += covers(o);
            BigDecimal[] m = dayMoney.get(d);
            m[0] = m[0].add(net(o));
            m[1] = m[1].add(billed(o));
        }
        List<RestaurantAnalysisDto.Day> days = new ArrayList<>();
        int[] wdDays = new int[8], wdChecks = new int[8], wdCovers = new int[8];
        BigDecimal[] wdNet = new BigDecimal[8];
        java.util.Arrays.fill(wdNet, BigDecimal.ZERO);
        for (Map.Entry<LocalDate, int[]> e : dayCount.entrySet()) {
            BigDecimal[] m = dayMoney.get(e.getKey());
            days.add(new RestaurantAnalysisDto.Day(e.getKey(), e.getValue()[0], e.getValue()[1], r2(m[0]), r2(m[1])));
            int w = e.getKey().getDayOfWeek().getValue();
            wdDays[w]++;
            wdChecks[w] += e.getValue()[0];
            wdCovers[w] += e.getValue()[1];
            wdNet[w] = wdNet[w].add(m[0]);
        }
        List<RestaurantAnalysisDto.Weekday> weekdays = new ArrayList<>();
        for (int w = 1; w <= 7; w++) {
            weekdays.add(new RestaurantAnalysisDto.Weekday(w, DayOfWeek.of(w).getDisplayName(TextStyle.FULL, Locale.ENGLISH), wdDays[w], wdChecks[w], wdCovers[w],
                    r2(wdNet[w]), div(wdNet[w], wdDays[w])));
        }

        // order types
        Map<String, Object[]> typeAcc = new LinkedHashMap<>();
        for (RestaurantOrder o : orders) {
            Object[] a = typeAcc.computeIfAbsent(typeOf(o), k -> new Object[]{0, 0, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = (int) a[0] + 1;
            a[1] = (int) a[1] + covers(o);
            a[2] = ((BigDecimal) a[2]).add(net(o));
            a[3] = ((BigDecimal) a[3]).add(billed(o));
        }
        List<RestaurantAnalysisDto.Type> types = new ArrayList<>();
        typeAcc.forEach((k, a) -> types.add(new RestaurantAnalysisDto.Type(k, (int) a[0], (int) a[1], r2((BigDecimal) a[2]), r2((BigDecimal) a[3]), div((BigDecimal) a[2], (int) a[0]))));
        types.sort(Comparator.comparing(RestaurantAnalysisDto.Type::netSales).reversed());

        // payments
        Map<String, BigDecimal> payAmount = new LinkedHashMap<>();
        Map<String, Set<Long>> payChecks = new LinkedHashMap<>();
        BigDecimal depositTotal = BigDecimal.ZERO;
        for (RestaurantOrder o : orders) {
            for (RestaurantPayment p : o.getPayments()) {
                payAmount.merge(p.getMethod(), p.getAmount(), BigDecimal::add);
                payChecks.computeIfAbsent(p.getMethod(), k -> new java.util.HashSet<>()).add(o.getId());
            }
            if (deposit(o).signum() > 0) {
                depositTotal = depositTotal.add(deposit(o));
                payAmount.merge("DEPOSIT", deposit(o), BigDecimal::add);
                payChecks.computeIfAbsent("DEPOSIT", k -> new java.util.HashSet<>()).add(o.getId());
            }
        }
        BigDecimal collected = payAmount.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<RestaurantAnalysisDto.Payment> payments = new ArrayList<>();
        payAmount.forEach((m, a) -> payments.add(new RestaurantAnalysisDto.Payment(m, payChecks.get(m).size(), r2(a), pct(a, collected))));
        payments.sort(Comparator.comparing(RestaurantAnalysisDto.Payment::amount).reversed());

        // menu mix
        Map<Long, MenuItem> menuByProduct = new LinkedHashMap<>();
        for (MenuItem mi : menuItemRepository.findAllByOrderBySortOrderAsc()) {
            if (mi.getProduct() != null) menuByProduct.putIfAbsent(mi.getProduct().getId(), mi);
        }
        record ItemAcc(Long productId, String name, String category, BigDecimal[] qty, BigDecimal[] net, BigDecimal[] cost) {
        }
        Map<Long, ItemAcc> itemAcc = new LinkedHashMap<>();
        record CatAcc(Long id, String name, BigDecimal[] qty, BigDecimal[] net, BigDecimal[] cost) {
        }
        Map<Long, CatAcc> catAcc = new LinkedHashMap<>();
        for (RestaurantOrder o : orders) {
            Map<Long, BigDecimal> ing = ingredientCost(o);
            for (RestaurantOrderLine l : o.getLines()) {
                if (l.getProduct() == null) continue;
                MenuItem mi = menuByProduct.get(l.getProduct().getId());
                MenuCategory cat = mi == null ? null : mi.getCategory();
                String catName = cat == null ? "Uncategorized" : cat.getName();
                ItemAcc a = itemAcc.computeIfAbsent(l.getProduct().getId(), k -> new ItemAcc(l.getProduct().getId(), l.getProduct().getName(), catName,
                        new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                BigDecimal ln = RestaurantOrderCalculator.lineNetTotal(l), lc = lineCost(l, ing);
                a.qty()[0] = a.qty()[0].add(l.getQuantity());
                a.net()[0] = a.net()[0].add(ln);
                a.cost()[0] = a.cost()[0].add(lc);
                CatAcc c = catAcc.computeIfAbsent(cat == null ? -1L : cat.getId(), k -> new CatAcc(cat == null ? null : cat.getId(), catName, new BigDecimal[]{BigDecimal.ZERO},
                        new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO}));
                c.qty()[0] = c.qty()[0].add(l.getQuantity());
                c.net()[0] = c.net()[0].add(ln);
                c.cost()[0] = c.cost()[0].add(lc);
            }
        }
        BigDecimal totalQty = itemAcc.values().stream().map(a -> a.qty()[0]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemNet = itemAcc.values().stream().map(a -> a.net()[0]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemCost = itemAcc.values().stream().map(a -> a.cost()[0]).reduce(BigDecimal.ZERO, BigDecimal::add);
        // menu engineering: popular = at least 70% of an even share of the dishes sold; profitable = unit profit at or above the menu's average
        BigDecimal avgUnitProfit = totalQty.signum() == 0 ? BigDecimal.ZERO : itemNet.subtract(itemCost).divide(totalQty, 6, RoundingMode.HALF_UP);
        BigDecimal popularLine = itemAcc.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(70).divide(BigDecimal.valueOf(itemAcc.size()), 6, RoundingMode.HALF_UP);
        List<RestaurantAnalysisDto.Item> itemRows = new ArrayList<>();
        for (ItemAcc a : itemAcc.values()) {
            BigDecimal profitI = a.net()[0].subtract(a.cost()[0]);
            BigDecimal unit = a.qty()[0].signum() == 0 ? BigDecimal.ZERO : profitI.divide(a.qty()[0], 4, RoundingMode.HALF_UP);
            BigDecimal pop = totalQty.signum() == 0 ? BigDecimal.ZERO : a.qty()[0].multiply(HUNDRED).divide(totalQty, 1, RoundingMode.HALF_UP);
            String cls = null;
            if (itemAcc.size() >= 2) {
                boolean popular = pop.compareTo(popularLine) >= 0, profitable = unit.compareTo(avgUnitProfit) >= 0;
                cls = popular && profitable ? "STAR" : popular ? "PLOWHORSE" : profitable ? "PUZZLE" : "DOG";
            }
            itemRows.add(new RestaurantAnalysisDto.Item(a.productId(), a.name(), a.category(), a.qty()[0], r2(a.net()[0]), pct(a.net()[0], itemNet), r2(a.cost()[0]),
                    r2(profitI), pct(profitI, a.net()[0]), r2(unit), pop, cls));
        }
        itemRows.sort(Comparator.comparing(RestaurantAnalysisDto.Item::netSales).reversed().thenComparing(RestaurantAnalysisDto.Item::name));
        List<RestaurantAnalysisDto.Category> catRows = new ArrayList<>();
        for (CatAcc c : catAcc.values()) {
            BigDecimal p = c.net()[0].subtract(c.cost()[0]);
            catRows.add(new RestaurantAnalysisDto.Category(c.id(), c.name(), c.qty()[0], r2(c.net()[0]), pct(c.net()[0], itemNet), r2(c.cost()[0]), r2(p), pct(p, c.net()[0])));
        }
        catRows.sort(Comparator.comparing(RestaurantAnalysisDto.Category::netSales).reversed());

        // controls (voids, comps, cancelled orders, receipt copies)
        List<RestaurantVoidLog> logs = voidLogRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(l -> !l.getCreatedAt().isBefore(start(from)) && !l.getCreatedAt().isAfter(end(to))).toList();

        // servers
        Map<String, Object[]> srv = new LinkedHashMap<>();
        for (RestaurantOrder o : orders) {
            AppUser s = o.getServer();
            String key = s == null ? "-" : s.getUsername();
            Object[] a = srv.computeIfAbsent(key, k -> new Object[]{s == null ? null : s.getId(), serverName(o), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[2] = (int) a[2] + 1;
            a[3] = (int) a[3] + covers(o);
            a[4] = ((BigDecimal) a[4]).add(net(o));
            a[5] = ((BigDecimal) a[5]).add(RestaurantOrderCalculator.serviceCharge(o));
            a[6] = ((BigDecimal) a[6]).add(tip(o));
            a[7] = ((BigDecimal) a[7]).add(RestaurantOrderCalculator.discountTotal(o));
        }
        List<RestaurantAnalysisDto.ServerRow> serverRows = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : srv.entrySet()) {
            Object[] a = e.getValue();
            int voids = 0, comps = 0;
            BigDecimal voidAmt = BigDecimal.ZERO, compAmt = BigDecimal.ZERO;
            for (RestaurantVoidLog l : logs) {
                if (l.getByUser() == null || !l.getByUser().equalsIgnoreCase(e.getKey())) continue;
                if ("VOID".equals(l.getEntryType())) {
                    voids++;
                    voidAmt = voidAmt.add(l.getAmount());
                } else if ("COMP".equals(l.getEntryType())) {
                    comps++;
                    compAmt = compAmt.add(l.getAmount());
                }
            }
            serverRows.add(new RestaurantAnalysisDto.ServerRow((Long) a[0], (String) a[1], (int) a[2], (int) a[3], r2((BigDecimal) a[4]), div((BigDecimal) a[4], (int) a[2]),
                    r2((BigDecimal) a[5]), r2((BigDecimal) a[6]), r2((BigDecimal) a[7]), voids, r2(voidAmt), comps, r2(compAmt)));
        }
        serverRows.sort(Comparator.comparing(RestaurantAnalysisDto.ServerRow::netSales).reversed());

        // tables
        Map<Long, Object[]> tbl = new LinkedHashMap<>();
        for (RestaurantOrder o : orders) {
            if (!dineInRoot(o) || o.getTable() == null) continue;
            DiningTable t = o.getTable();
            Object[] a = tbl.computeIfAbsent(t.getId(), k -> new Object[]{t.getName(), t.getSection(), t.getCapacity(), 0, 0, 0L, BigDecimal.ZERO});
            a[3] = (int) a[3] + 1;
            a[4] = (int) a[4] + o.getGuestCount();
            a[5] = (long) a[5] + Math.max(0, Duration.between(o.getOpenedAt(), partyEnd.getOrDefault(o.getId(), o.getClosedAt())).toMinutes());
            a[6] = ((BigDecimal) a[6]).add(partyNet.getOrDefault(o.getId(), BigDecimal.ZERO));
        }
        long dayCountTotal = Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays();
        List<RestaurantAnalysisDto.TableRow> tableRows = new ArrayList<>();
        for (Map.Entry<Long, Object[]> e : tbl.entrySet()) {
            Object[] a = e.getValue();
            int p = (int) a[3];
            tableRows.add(new RestaurantAnalysisDto.TableRow(e.getKey(), (String) a[0], (String) a[1], (int) a[2], p, (int) a[4],
                    BigDecimal.valueOf(p).divide(BigDecimal.valueOf(Math.max(1, dayCountTotal)), 2, RoundingMode.HALF_UP), p == 0 ? 0 : (int) ((long) a[5] / p),
                    r2((BigDecimal) a[6]), div((BigDecimal) a[6], (int) a[4])));
        }
        tableRows.sort(Comparator.comparing(RestaurantAnalysisDto.TableRow::netSales).reversed());

        return new RestaurantAnalysisDto(from, to, totals, hours, weekdays, days, types, payments, itemRows, catRows, serverRows, tableRows, controls(logs));
    }

    private static List<RestaurantAnalysisDto.Control> controls(List<RestaurantVoidLog> logs) {
        Map<String, Object[]> m = new TreeMap<>();
        for (RestaurantVoidLog l : logs) {
            Object[] a = m.computeIfAbsent(l.getEntryType(), k -> new Object[]{0, BigDecimal.ZERO});
            a[0] = (int) a[0] + 1;
            a[1] = ((BigDecimal) a[1]).add(l.getAmount() == null ? BigDecimal.ZERO : l.getAmount());
        }
        List<RestaurantAnalysisDto.Control> out = new ArrayList<>();
        m.forEach((k, a) -> out.add(new RestaurantAnalysisDto.Control(k, (int) a[0], r2((BigDecimal) a[1]))));
        return out;
    }

    // ------------------------------------------------------------------ end of day

    /** The cash left in the drawer at the last close before {@code date}. */
    private BigDecimal suggestedFloat(LocalDate date) {
        return closeRepository.findFirstByStatusAndBusinessDateLessThanOrderByBusinessDateDesc("CLOSED", date)
                .map(c -> r2(c.getFloatKept())).orElse(ZERO);
    }

    @Transactional(readOnly = true)
    public EndOfDayDto endOfDay(LocalDate dateParam, BigDecimal openingFloatParam) {
        LocalDate date = dateParam != null ? dateParam : clock.today();
        RestaurantDayClose close = closeRepository.findByBusinessDateAndStatus(date, "CLOSED").orElse(null);
        // A reopened day keeps the float it was closed with, so counting again gives the same expected cash.
        BigDecimal floatUsed = close != null ? close.getOpeningFloat() : openingFloatParam != null ? openingFloatParam
                : closeRepository.findFirstByBusinessDateAndStatusOrderByIdDesc(date, "REOPENED").map(c -> r2(c.getOpeningFloat())).orElseGet(() -> suggestedFloat(date));
        EndOfDayDto live = build(date, floatUsed);
        if (close == null) return live;
        try {
            EndOfDayDto frozen = objectMapper.readValue(close.getSnapshot(), EndOfDayDto.class);
            boolean changed = live.sales().checks() != frozen.sales().checks() || live.sales().billed().compareTo(frozen.sales().billed()) != 0
                    || live.sales().tips().compareTo(frozen.sales().tips()) != 0 || live.cash().expected().compareTo(frozen.cash().expected()) != 0;
            return new EndOfDayDto(frozen.date(), true, close.getZNumber(), close.getClosedBy(), close.getClosedAt(), changed, frozen.sales(), frozen.types(), frozen.taxes(),
                    frozen.payments(), frozen.tips(), frozen.deposits(), frozen.controls(), frozen.openOrders(), frozen.cash(), frozen.cashEvents(), close.getNotes());
        } catch (Exception e) {
            return new EndOfDayDto(live.date(), true, close.getZNumber(), close.getClosedBy(), close.getClosedAt(), true, live.sales(), live.types(), live.taxes(), live.payments(),
                    live.tips(), live.deposits(), live.controls(), live.openOrders(), live.cash(), live.cashEvents(), close.getNotes());
        }
    }

    private EndOfDayDto build(LocalDate date, BigDecimal openingFloat) {
        List<RestaurantOrder> orders = closedOrders(date, date);
        Set<String> cashCodes = paymentMethodService.listCashCodes();
        Set<String> roomCodes = paymentMethodService.listRoomChargeCodes();

        int parties = 0, covers = 0;
        BigDecimal itemsSold = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO, discounts = BigDecimal.ZERO, net = BigDecimal.ZERO, service = BigDecimal.ZERO, tax = BigDecimal.ZERO,
                rounding = BigDecimal.ZERO, billed = BigDecimal.ZERO, tips = BigDecimal.ZERO, depositApplied = BigDecimal.ZERO;
        Map<String, Object[]> typeAcc = new LinkedHashMap<>();
        Map<String, BigDecimal[]> taxAcc = new TreeMap<>(Comparator.comparing((String s) -> s.equals("Service charge") ? 1 : 0).thenComparing(Comparator.naturalOrder()));
        Map<String, BigDecimal> payAmount = new LinkedHashMap<>();
        Map<String, Set<Long>> payChecks = new LinkedHashMap<>();
        Map<String, Object[]> tipAcc = new TreeMap<>();
        for (RestaurantOrder o : orders) {
            if (o.getParentOrderId() == null) parties++;
            covers += covers(o);
            itemsSold = itemsSold.add(items(o));
            gross = gross.add(gross(o));
            discounts = discounts.add(RestaurantOrderCalculator.discountTotal(o));
            net = net.add(net(o));
            service = service.add(RestaurantOrderCalculator.serviceCharge(o));
            tax = tax.add(RestaurantOrderCalculator.taxTotal(o));
            rounding = rounding.add(RestaurantOrderCalculator.roundingAdjustment(o));
            billed = billed.add(billed(o));
            tips = tips.add(tip(o));
            depositApplied = depositApplied.add(deposit(o));
            Object[] t = typeAcc.computeIfAbsent(typeOf(o), k -> new Object[]{0, 0, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = (int) t[0] + 1;
            t[1] = (int) t[1] + covers(o);
            t[2] = ((BigDecimal) t[2]).add(net(o));
            t[3] = ((BigDecimal) t[3]).add(billed(o));
            for (RestaurantOrderLine l : o.getLines()) {
                BigDecimal rate = l.getTaxRatePercent() == null ? BigDecimal.ZERO : l.getTaxRatePercent();
                String label = rate.signum() == 0 ? "No tax" : "Tax " + rate.stripTrailingZeros().toPlainString() + "%";
                BigDecimal[] a = taxAcc.computeIfAbsent(label, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                a[0] = a[0].add(RestaurantOrderCalculator.lineNetTotal(l));
                a[1] = a[1].add(RestaurantOrderCalculator.lineTax(l));
            }
            BigDecimal sc = RestaurantOrderCalculator.serviceCharge(o);
            if (sc.signum() > 0) {
                BigDecimal[] a = taxAcc.computeIfAbsent("Service charge", k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                a[0] = a[0].add(sc);
                a[1] = a[1].add(RestaurantOrderCalculator.serviceChargeTax(o));
            }
            for (RestaurantPayment p : o.getPayments()) {
                payAmount.merge(p.getMethod(), p.getAmount(), BigDecimal::add);
                payChecks.computeIfAbsent(p.getMethod(), k -> new java.util.HashSet<>()).add(o.getId());
            }
            if (deposit(o).signum() > 0) {
                payAmount.merge("DEPOSIT", deposit(o), BigDecimal::add);
                payChecks.computeIfAbsent("DEPOSIT", k -> new java.util.HashSet<>()).add(o.getId());
            }
            if (tip(o).signum() > 0) {
                Object[] a = tipAcc.computeIfAbsent(serverName(o), k -> new Object[]{0, BigDecimal.ZERO});
                a[0] = (int) a[0] + 1;
                a[1] = ((BigDecimal) a[1]).add(tip(o));
            }
        }
        BigDecimal collected = BigDecimal.ZERO, cashSales = BigDecimal.ZERO;
        List<EndOfDayDto.Payment> payments = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : payAmount.entrySet()) {
            String kind = e.getKey().equals("DEPOSIT") ? "DEPOSIT" : cashCodes.contains(e.getKey()) ? "CASH" : roomCodes.contains(e.getKey()) ? "ROOM" : "OTHER";
            if (kind.equals("CASH")) cashSales = cashSales.add(e.getValue());
            collected = collected.add(e.getValue());
            payments.add(new EndOfDayDto.Payment(e.getKey(), kind, payChecks.get(e.getKey()).size(), r2(e.getValue())));
        }
        payments.sort(Comparator.comparing((EndOfDayDto.Payment p) -> p.kind().equals("DEPOSIT") ? 1 : 0).thenComparing(Comparator.comparing(EndOfDayDto.Payment::amount).reversed()));

        List<RestaurantAnalysisDto.Type> types = new ArrayList<>();
        typeAcc.forEach((k, a) -> types.add(new RestaurantAnalysisDto.Type(k, (int) a[0], (int) a[1], r2((BigDecimal) a[2]), r2((BigDecimal) a[3]), div((BigDecimal) a[2], (int) a[0]))));
        List<EndOfDayDto.Tax> taxes = new ArrayList<>();
        taxAcc.forEach((k, a) -> taxes.add(new EndOfDayDto.Tax(k, r2(a[0]), r2(a[1]))));
        List<EndOfDayDto.TipRow> tipRows = new ArrayList<>();
        tipAcc.forEach((k, a) -> tipRows.add(new EndOfDayDto.TipRow(k, (int) a[0], r2((BigDecimal) a[1]))));

        // cash events of the day
        List<RestaurantCashEvent> events = cashEventRepository.findByCreatedAtBetweenOrderByCreatedAtAscIdAsc(start(date), end(date));
        Map<String, Object[]> dep = new LinkedHashMap<>();
        BigDecimal depIn = BigDecimal.ZERO, depOut = BigDecimal.ZERO, paidIn = BigDecimal.ZERO, paidOut = BigDecimal.ZERO, tipsPaid = BigDecimal.ZERO;
        for (RestaurantCashEvent e : events) {
            boolean cash = e.getMethod() != null && cashCodes.contains(e.getMethod());
            switch (e.getKind()) {
                case "DEPOSIT_RECEIVED", "DEPOSIT_REFUNDED", "DEPOSIT_FORFEITED" -> {
                    Object[] a = dep.computeIfAbsent(e.getKind(), k -> new Object[]{0, BigDecimal.ZERO, BigDecimal.ZERO});
                    a[0] = (int) a[0] + 1;
                    a[1] = ((BigDecimal) a[1]).add(e.getAmount());
                    if (cash && !e.getKind().equals("DEPOSIT_FORFEITED")) a[2] = ((BigDecimal) a[2]).add(e.getAmount());
                    if (cash && e.getKind().equals("DEPOSIT_RECEIVED")) depIn = depIn.add(e.getAmount());
                    if (cash && e.getKind().equals("DEPOSIT_REFUNDED")) depOut = depOut.add(e.getAmount());
                }
                case "PAID_IN" -> paidIn = paidIn.add(e.getAmount());
                case "PAID_OUT" -> paidOut = paidOut.add(e.getAmount());
                case "TIPS_PAID" -> tipsPaid = tipsPaid.add(e.getAmount());
                default -> {
                }
            }
        }
        List<EndOfDayDto.Deposit> deposits = new ArrayList<>();
        for (String k : List.of("DEPOSIT_RECEIVED", "DEPOSIT_REFUNDED", "DEPOSIT_FORFEITED")) {
            Object[] a = dep.get(k);
            if (a != null) deposits.add(new EndOfDayDto.Deposit(k, (int) a[0], r2((BigDecimal) a[1]), r2((BigDecimal) a[2])));
        }
        List<EndOfDayDto.CashEventDto> eventDtos = events.stream().map(RestaurantCashService::dto).toList();

        BigDecimal expected = r2(openingFloat).add(cashSales).add(depIn).subtract(depOut).add(paidIn).subtract(paidOut).subtract(tipsPaid);

        // tables still open at the end of this day
        List<String> openLabels = new ArrayList<>();
        BigDecimal openValue = BigDecimal.ZERO;
        for (RestaurantOrder o : orderRepository.findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus.OPEN)) {
            if (o.getOpenedAt().isAfter(end(date))) continue;
            openLabels.add(o.displayLabel());
            openValue = openValue.add(RestaurantOrderCalculator.total(o));
        }
        List<RestaurantVoidLog> logs = voidLogRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(l -> !l.getCreatedAt().isBefore(start(date)) && !l.getCreatedAt().isAfter(end(date))).toList();

        var sales = new EndOfDayDto.Sales(orders.size(), parties, covers, itemsSold, r2(gross), r2(discounts), r2(net), r2(service), r2(tax), r2(rounding), r2(billed), r2(tips),
                r2(depositApplied), r2(collected));
        var cash = new EndOfDayDto.Cash(r2(openingFloat), r2(cashSales), r2(depIn), r2(depOut), r2(paidIn), r2(paidOut), r2(tipsPaid), r2(expected), null, null, null, null);
        return new EndOfDayDto(date, false, null, null, null, false, sales, types, taxes, payments, tipRows, deposits, controls(logs),
                new EndOfDayDto.OpenOrders(openLabels.size(), r2(openValue), openLabels), cash, eventDtos, null);
    }

    @Transactional
    public EndOfDayDto close(DayCloseRequest request) {
        if (!RestaurantAccess.isApprover()) throw ApiException.forbidden("Only a manager can close the day");
        LocalDate today = clock.today();
        LocalDate date = request.getDate() != null ? request.getDate() : today;
        if (date.isAfter(today)) throw ApiException.badRequest("A day that has not happened yet cannot be closed");
        RestaurantDayClose existing = closeRepository.findByBusinessDateAndStatus(date, "CLOSED").orElse(null);
        if (existing != null) throw ApiException.badRequest("This day is already closed (Z-" + String.format("%04d", existing.getZNumber()) + ")");
        BigDecimal counted = request.getCashCounted();
        if (counted == null || counted.signum() < 0 || counted.compareTo(BigDecimal.valueOf(1_000_000_000)) > 0) throw ApiException.badRequest("Enter the cash counted in the drawer");
        counted = counted.setScale(2, RoundingMode.HALF_UP);
        BigDecimal openingFloat = request.getOpeningFloat() != null ? request.getOpeningFloat().setScale(2, RoundingMode.HALF_UP)
                : closeRepository.findFirstByBusinessDateAndStatusOrderByIdDesc(date, "REOPENED").map(c -> r2(c.getOpeningFloat())).orElseGet(() -> suggestedFloat(date));
        if (openingFloat.signum() < 0) throw ApiException.badRequest("The opening float cannot be negative");
        EndOfDayDto live = build(date, openingFloat);
        if (live.openOrders().count() > 0 && !request.isCloseWithOpenOrders()) {
            throw ApiException.badRequest(live.openOrders().count() + " order" + (live.openOrders().count() == 1 ? " is" : "s are") + " still open ("
                    + String.join(", ", live.openOrders().labels().stream().limit(5).toList()) + "). Settle or cancel them, or close anyway.");
        }
        BigDecimal variance = counted.subtract(live.cash().expected());
        String notes = request.getNotes() == null ? "" : request.getNotes().trim();
        if (notes.length() > 300) throw ApiException.badRequest("The note is too long (300 characters at most)");
        if (variance.signum() != 0 && notes.isEmpty()) throw ApiException.badRequest("The drawer is " + (variance.signum() > 0 ? "over" : "short") + " by " + variance.abs() + "; write a note to explain it");
        BigDecimal floatKept = request.getFloatKept() != null ? request.getFloatKept().setScale(2, RoundingMode.HALF_UP) : counted.min(openingFloat);
        if (floatKept.signum() < 0 || floatKept.compareTo(counted) > 0) throw ApiException.badRequest("The cash left in the drawer must be between zero and the cash counted");
        BigDecimal banked = counted.subtract(floatKept);

        var cash = new EndOfDayDto.Cash(live.cash().openingFloat(), live.cash().cashSales(), live.cash().depositsIn(), live.cash().depositsOut(), live.cash().paidIn(),
                live.cash().paidOut(), live.cash().tipsPaid(), live.cash().expected(), counted, variance, floatKept, banked);
        int z = closeRepository.maxZ() + 1;
        Instant now = Instant.now();
        String user = RestaurantAccess.username();
        EndOfDayDto frozen = new EndOfDayDto(date, true, z, user, now, false, live.sales(), live.types(), live.taxes(), live.payments(), live.tips(), live.deposits(), live.controls(),
                live.openOrders(), cash, live.cashEvents(), notes.isEmpty() ? null : notes);
        RestaurantDayClose c = new RestaurantDayClose();
        c.setBusinessDate(date);
        c.setZNumber(z);
        c.setOpeningFloat(openingFloat);
        c.setCashExpected(live.cash().expected());
        c.setCashCounted(counted);
        c.setVariance(variance);
        c.setFloatKept(floatKept);
        c.setBanked(banked);
        c.setChecks(live.sales().checks());
        c.setNetSales(live.sales().netSales());
        c.setTotalBilled(live.sales().billed());
        c.setTips(live.sales().tips());
        c.setNotes(notes.isEmpty() ? null : notes);
        c.setClosedBy(user);
        c.setClosedAt(now);
        try {
            c.setSnapshot(objectMapper.writeValueAsString(frozen));
        } catch (Exception e) {
            throw ApiException.badRequest("The day could not be saved: " + e.getMessage());
        }
        closeRepository.save(c);
        auditLogService.record("RESTAURANT_DAY_CLOSED", "Closed " + date + " as Z-" + String.format("%04d", z) + " (takings " + live.sales().billed() + ", cash " + counted
                + (variance.signum() == 0 ? "" : ", variance " + variance) + ")", "RestaurantDayClose", c.getId());
        return frozen;
    }

    @Transactional
    public EndOfDayDto reopen(LocalDate date, String reason) {
        if (!RestaurantAccess.isApprover()) throw ApiException.forbidden("Only a manager can reopen a closed day");
        RestaurantDayClose c = closeRepository.findByBusinessDateAndStatus(date, "CLOSED").orElseThrow(() -> ApiException.badRequest("That day is not closed"));
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw ApiException.badRequest("Say why the day is being reopened");
        if (why.length() > 200) throw ApiException.badRequest("The reason is too long (200 characters at most)");
        c.setStatus("REOPENED");
        c.setReopenedBy(RestaurantAccess.username());
        c.setReopenedAt(Instant.now());
        c.setReopenReason(why);
        closeRepository.save(c);
        auditLogService.record("RESTAURANT_DAY_REOPENED", "Reopened " + date + " (was Z-" + String.format("%04d", c.getZNumber()) + "): " + why, "RestaurantDayClose", c.getId());
        return endOfDay(date, null);
    }

    @Transactional(readOnly = true)
    public List<DayCloseRowDto> history(String status, LocalDate from, LocalDate to) {
        return closeRepository.findAllByOrderByBusinessDateDescIdDesc().stream()
                .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase("ALL") || status.equalsIgnoreCase(c.getStatus()))
                .filter(c -> (from == null || !c.getBusinessDate().isBefore(from)) && (to == null || !c.getBusinessDate().isAfter(to)))
                .map(DayCloseRowDto::from).toList();
    }
}
