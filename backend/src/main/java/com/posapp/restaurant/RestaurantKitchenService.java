package com.posapp.restaurant;
import com.posapp.common.Qty;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.HotelClock;
import com.posapp.restaurant.dto.ExpoOrderDto;
import com.posapp.restaurant.dto.KitchenSummaryDto;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The kitchen side of the restaurant: the expo (pass) feed, the station summary, bumping and serving whole orders, and sending a dish back. */
@Service
public class RestaurantKitchenService {

    private static final String UNASSIGNED = "Unassigned";

    private final RestaurantOrderRepository orderRepository;
    private final RestaurantOrderLineRepository lineRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantSettingsService settingsService;
    private final AuditLogService auditLogService;
    private final HotelClock clock;

    public RestaurantKitchenService(RestaurantOrderRepository orderRepository, RestaurantOrderLineRepository lineRepository,
                                    MenuItemRepository menuItemRepository, RestaurantSettingsService settingsService,
                                    AuditLogService auditLogService, HotelClock clock) {
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
        this.menuItemRepository = menuItemRepository;
        this.settingsService = settingsService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    private static boolean cooking(RestaurantOrderLine l) {
        return l.getKitchenStatus() == KitchenStatus.SENT || l.getKitchenStatus() == KitchenStatus.PREPARING;
    }

    private static boolean live(RestaurantOrderLine l) {
        return cooking(l) || l.getKitchenStatus() == KitchenStatus.READY;
    }

    private static String stationOf(RestaurantOrderLine l) {
        return l.getStation() == null || l.getStation().isBlank() ? UNASSIGNED : l.getStation();
    }

    /** Every open order that has something in the kitchen, the complete ones first and then the oldest. */
    @Transactional(readOnly = true)
    public List<ExpoOrderDto> expo() {
        RestaurantSettings cfg = settingsService.entity();
        Instant now = Instant.now();
        List<ExpoOrderDto> out = new ArrayList<>();
        for (RestaurantOrder o : orderRepository.findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus.OPEN)) {
            List<RestaurantOrderLine> live = o.getLines().stream().filter(RestaurantKitchenService::live).toList();
            if (live.isEmpty()) continue;
            List<ExpoOrderDto.Line> lines = new ArrayList<>();
            java.util.Set<String> waiting = new java.util.TreeSet<>();
            int ready = 0, cook = 0;
            String worst = "OK";
            Instant oldest = null;
            for (RestaurantOrderLine l : live) {
                String urgency = KitchenTiming.urgency(l, now, cfg.getDefaultPrepMinutes(), cfg.getKitchenWarnPercent());
                if (cooking(l)) {
                    cook++;
                    waiting.add(stationOf(l));
                    if (urgency.equals("LATE") || (urgency.equals("WARN") && !worst.equals("LATE"))) worst = urgency;
                } else {
                    ready++;
                }
                if (l.getSentAt() != null && (oldest == null || l.getSentAt().isBefore(oldest))) oldest = l.getSentAt();
                var conflict = RestaurantAllergens.overlap(l.getAllergens(), o.getGuestAllergens());
                lines.add(new ExpoOrderDto.Line(l.getId(), l.getDescription(), l.getQuantity(), stationOf(l), l.getKitchenStatus().name(), l.getCourse(), l.getSeat(),
                        l.getOptions().stream().map(RestaurantOrderLineOption::getOptionName).toList(), l.getNotes(), urgency,
                        (int) (KitchenTiming.elapsedSeconds(l, now) / 60), KitchenTiming.target(l, cfg.getDefaultPrepMinutes()),
                        conflict.isEmpty() ? null : String.join(",", conflict)));
            }
            int held = (int) o.getLines().stream().filter(l -> l.getKitchenStatus() == KitchenStatus.PENDING).count();
            Instant from = oldest != null ? oldest : o.getOpenedAt();
            out.add(new ExpoOrderDto(o.getId(), o.displayLabel(), o.getOrderType().name(), o.getGuestCount(),
                    o.getServer() != null ? o.getServer().getFullName() : null, from, (int) (java.time.Duration.between(from, now).toMinutes()),
                    cook == 0, ready, cook, held, new ArrayList<>(waiting), worst, o.getGuestAllergens(), lines));
        }
        out.sort(Comparator.comparing((ExpoOrderDto e) -> !e.allReady()).thenComparing(ExpoOrderDto::oldestSentAt));
        return out;
    }

    @Transactional(readOnly = true)
    public KitchenSummaryDto summary() {
        RestaurantSettings cfg = settingsService.entity();
        Instant now = Instant.now();
        Map<String, int[]> st = new TreeMap<>(); // waiting, cooking, ready, late, warn, doneToday
        Map<String, Long> prepSecs = new LinkedHashMap<>();
        int waiting = 0, cooking = 0, ready = 0, late = 0;
        long oldest = 0;
        for (RestaurantOrder o : orderRepository.findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus.OPEN)) {
            for (RestaurantOrderLine l : o.getLines()) {
                if (!live(l)) continue;
                int[] a = st.computeIfAbsent(stationOf(l), k -> new int[6]);
                String urgency = KitchenTiming.urgency(l, now, cfg.getDefaultPrepMinutes(), cfg.getKitchenWarnPercent());
                switch (l.getKitchenStatus()) {
                    case SENT -> { a[0]++; waiting++; }
                    case PREPARING -> { a[1]++; cooking++; }
                    default -> { a[2]++; ready++; }
                }
                if (urgency.equals("LATE")) { a[3]++; late++; }
                if (urgency.equals("WARN")) a[4]++;
                if (cooking(l)) oldest = Math.max(oldest, KitchenTiming.elapsedSeconds(l, now) / 60);
            }
        }
        Instant dayStart = clock.today().atStartOfDay(clock.zone()).toInstant();
        for (RestaurantOrderLine l : lineRepository.findByReadyAtBetween(dayStart, now.plusSeconds(60))) {
            if (l.getSentAt() == null || l.getReadyAt() == null) continue;
            String s = stationOf(l);
            st.computeIfAbsent(s, k -> new int[6])[5]++;
            prepSecs.merge(s, java.time.Duration.between(l.getSentAt(), l.getReadyAt()).getSeconds(), Long::sum);
        }
        List<KitchenSummaryDto.Station> stations = new ArrayList<>();
        for (Map.Entry<String, int[]> e : st.entrySet()) {
            int[] a = e.getValue();
            double avg = a[5] == 0 ? 0 : Math.round(prepSecs.getOrDefault(e.getKey(), 0L) / 60.0 / a[5] * 10) / 10.0;
            stations.add(new KitchenSummaryDto.Station(e.getKey(), a[0], a[1], a[2], a[3], a[4], avg, a[5]));
        }
        List<String> soldOut = menuItemRepository.findAll().stream().filter(MenuItem::isSoldOut)
                .map(m -> m.getProduct().getName()).sorted().toList();
        return new KitchenSummaryDto(stations, waiting, cooking, ready, late, (int) oldest, soldOut);
    }

    /** Clears a station's dishes on one order in one tap: everything waiting or cooking there becomes ready. */
    @Transactional
    public RestaurantOrderDto bump(Long orderId, String station) {
        RestaurantOrder o = open(orderId);
        String want = station == null || station.isBlank() ? null : station.trim();
        int n = 0;
        for (RestaurantOrderLine l : o.getLines()) {
            if (!cooking(l)) continue;
            if (want != null && !stationOf(l).equalsIgnoreCase(want)) continue;
            l.moveTo(KitchenStatus.READY);
            n++;
        }
        if (n == 0) throw ApiException.badRequest("Nothing is cooking" + (want == null ? "" : " at " + want) + " on this order");
        return RestaurantOrderDto.from(orderRepository.save(o));
    }

    /** The pass takes everything that is ready out to the table. */
    @Transactional
    public RestaurantOrderDto serveReady(Long orderId) {
        RestaurantOrder o = open(orderId);
        int n = 0;
        for (RestaurantOrderLine l : o.getLines()) {
            if (l.getKitchenStatus() == KitchenStatus.READY) {
                l.moveTo(KitchenStatus.SERVED);
                n++;
            }
        }
        if (n == 0) throw ApiException.badRequest("Nothing is ready to serve on this order");
        auditLogService.record("RESTAURANT_ORDER_SERVED", "Served " + n + " item" + (n == 1 ? "" : "s") + " at " + o.displayLabel(), "RestaurantOrder", o.getId());
        return RestaurantOrderDto.from(orderRepository.save(o));
    }

    /** Sends a dish back to be made again (burnt, wrong, sent back by the guest): it returns to the kitchen with a fresh clock. */
    @Transactional
    public RestaurantOrderDto refire(Long orderId, Long lineId, String reason) {
        RestaurantOrder o = open(orderId);
        RestaurantOrderLine l = o.getLines().stream().filter(x -> x.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Line not found: " + lineId));
        if (l.getKitchenStatus() == KitchenStatus.PENDING) throw ApiException.badRequest("This item has not been sent to the kitchen yet");
        String why = reason == null ? "" : reason.trim();
        if (why.length() > 120) throw ApiException.badRequest("The reason is too long (120 characters at most)");
        l.setSentAt(null);
        l.moveTo(KitchenStatus.SENT);
        l.setRemakeCount(l.getRemakeCount() + 1);
        auditLogService.record("RESTAURANT_ITEM_REFIRED", "Sent back to the kitchen: " + Qty.str(l.getQuantity()) + "× " + l.getDescription() + " at " + o.displayLabel() + (why.isEmpty() ? "" : " (" + why + ")"), "RestaurantOrder", o.getId());
        return RestaurantOrderDto.from(orderRepository.save(o));
    }

    private RestaurantOrder open(Long id) {
        RestaurantOrder o = orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        if (o.getStatus() != RestaurantOrderStatus.OPEN) throw ApiException.badRequest("This order is already " + o.getStatus().name().toLowerCase());
        return o;
    }
}
