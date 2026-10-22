package com.posapp.restaurant;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.restaurant.dto.OpenTableOrderRequest;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import com.posapp.restaurant.dto.RestaurantWaitlistDto;
import com.posapp.restaurant.dto.RestaurantWaitlistRequest;
import com.posapp.restaurant.dto.SeatReservationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The line at the door: walk-in parties waiting for a table, how long they will wait, and seating them when one frees up. */
@Service
public class RestaurantWaitlistService {

    private static final Set<String> ACTIVE = Set.of("WAITING", "NOTIFIED");

    private final RestaurantWaitlistRepository repository;
    private final DiningTableRepository tableRepository;
    private final RestaurantOrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantOrderService orderService;
    private final RestaurantSettingsService settingsService;
    private final AuditLogService auditLogService;

    public RestaurantWaitlistService(RestaurantWaitlistRepository repository, DiningTableRepository tableRepository,
                                     RestaurantOrderRepository orderRepository, ReservationRepository reservationRepository,
                                     RestaurantOrderService orderService, RestaurantSettingsService settingsService,
                                     AuditLogService auditLogService) {
        this.repository = repository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.orderService = orderService;
        this.settingsService = settingsService;
        this.auditLogService = auditLogService;
    }

    /** {@code status}: blank = the people still waiting; ALL = everyone; or one status. */
    @Transactional(readOnly = true)
    public List<RestaurantWaitlistDto> list(String q, String status) {
        List<RestaurantWaitlist> all = repository.findAllByOrderByCreatedAtAscIdAsc();
        List<RestaurantWaitlist> queue = all.stream().filter(w -> ACTIVE.contains(w.getStatus())).toList();
        List<DiningTable> free = freeTables();
        List<RestaurantWaitlistDto> out = new ArrayList<>();
        for (RestaurantWaitlist w : all) {
            if (status == null || status.isBlank()) {
                if (!ACTIVE.contains(w.getStatus())) continue;
            } else if (!status.equalsIgnoreCase("ALL") && !w.getStatus().equalsIgnoreCase(status.trim())) {
                continue;
            }
            if (!com.posapp.common.ListPaging.has(q, w.getGuestName(), w.getGuestPhone(), w.getNotes())) continue;
            out.add(dto(w, queue, free));
        }
        return out;
    }

    @Transactional
    public RestaurantWaitlistDto add(RestaurantWaitlistRequest request) {
        if (request.getPartySize() < 1 || request.getPartySize() > 50) throw ApiException.badRequest("Party size must be between 1 and 50");
        if (request.getGuestName().trim().length() > 160) throw ApiException.badRequest("The name is too long");
        if (request.getNotes() != null && request.getNotes().length() > 200) throw ApiException.badRequest("The note is too long (200 characters at most)");
        Integer quoted = request.getQuotedMinutes();
        if (quoted != null && (quoted < 0 || quoted > 480)) throw ApiException.badRequest("The wait must be between 0 and 480 minutes");
        List<RestaurantWaitlist> queue = repository.findByStatusInOrderByCreatedAtAscIdAsc(ACTIVE);
        RestaurantWaitlist w = new RestaurantWaitlist();
        w.setGuestName(request.getGuestName().trim());
        w.setGuestPhone(request.getGuestPhone() == null || request.getGuestPhone().isBlank() ? null : request.getGuestPhone().trim());
        w.setPartySize(request.getPartySize());
        w.setNotes(request.getNotes() == null || request.getNotes().isBlank() ? null : request.getNotes().trim());
        w.setQuotedMinutes(quoted != null ? quoted : estimate(request.getPartySize(), queue.size()));
        w.setDesiredAt(Instant.now().plus(Duration.ofMinutes(w.getQuotedMinutes())));
        RestaurantWaitlist saved = repository.save(w);
        auditLogService.record("RESTAURANT_WAITLIST_ADDED", "Added " + saved.getGuestName() + " (" + saved.getPartySize() + ") to the waiting list", "RestaurantWaitlist", saved.getId());
        List<RestaurantWaitlist> after = new ArrayList<>(queue);
        after.add(saved);
        return dto(saved, after, freeTables());
    }

    @Transactional
    public RestaurantWaitlistDto notifyGuest(Long id) {
        RestaurantWaitlist w = open(id);
        w.setStatus("NOTIFIED");
        w.setNotifiedAt(Instant.now());
        repository.save(w);
        auditLogService.record("RESTAURANT_WAITLIST_NOTIFIED", "Told " + w.getGuestName() + " their table is ready", "RestaurantWaitlist", w.getId());
        return one(w);
    }

    @Transactional
    public RestaurantOrderDto seat(Long id, SeatReservationRequest request) {
        RestaurantWaitlist w = open(id);
        if (request == null || request.getTableId() == null) throw ApiException.badRequest("Choose the table to seat them at");
        OpenTableOrderRequest open = new OpenTableOrderRequest();
        open.setGuestCount(w.getPartySize());
        open.setServerId(request.getServerId());
        open.setNotes("Seated from the waiting list: " + w.getGuestName());
        RestaurantOrderDto order = orderService.openOrder(request.getTableId(), open);
        w.setStatus("SEATED");
        w.setClosedAt(Instant.now());
        w.setOrderId(order.id());
        repository.save(w);
        auditLogService.record("RESTAURANT_WAITLIST_SEATED", "Seated " + w.getGuestName() + " from the waiting list", "RestaurantWaitlist", w.getId());
        return order;
    }

    @Transactional
    public RestaurantWaitlistDto leave(Long id) {
        RestaurantWaitlist w = open(id);
        w.setStatus("LEFT");
        w.setClosedAt(Instant.now());
        repository.save(w);
        auditLogService.record("RESTAURANT_WAITLIST_LEFT", w.getGuestName() + " left the waiting list", "RestaurantWaitlist", w.getId());
        return one(w);
    }

    private RestaurantWaitlist open(Long id) {
        RestaurantWaitlist w = repository.findById(id).orElseThrow(() -> ApiException.notFound("Waiting list entry not found: " + id));
        if (!ACTIVE.contains(w.getStatus())) throw ApiException.badRequest(w.getGuestName() + " is already " + w.getStatus().toLowerCase() + " on the waiting list");
        return w;
    }

    private RestaurantWaitlistDto one(RestaurantWaitlist w) {
        return dto(w, repository.findByStatusInOrderByCreatedAtAscIdAsc(ACTIVE), freeTables());
    }

    private RestaurantWaitlistDto dto(RestaurantWaitlist w, List<RestaurantWaitlist> queue, List<DiningTable> free) {
        int position = 0;
        if (ACTIVE.contains(w.getStatus())) {
            for (int i = 0; i < queue.size(); i++) if (queue.get(i).getId().equals(w.getId())) position = i + 1;
        }
        Instant end = w.getClosedAt() != null ? w.getClosedAt() : Instant.now();
        int waited = (int) Math.max(0, Duration.between(w.getCreatedAt(), end).toMinutes());
        String ready = null;
        if (ACTIVE.contains(w.getStatus())) {
            ready = free.stream().filter(t -> t.getCapacity() >= w.getPartySize())
                    .min(java.util.Comparator.comparingInt(DiningTable::getCapacity).thenComparing(DiningTable::getName))
                    .map(DiningTable::getName).orElse(null);
        }
        return new RestaurantWaitlistDto(w.getId(), w.getGuestName(), w.getGuestPhone(), w.getPartySize(), w.getNotes(), w.getStatus(),
                w.getQuotedMinutes() == null ? 0 : w.getQuotedMinutes(), w.getCreatedAt(), w.getNotifiedAt(), w.getClosedAt(), w.getOrderId(),
                position, waited, ready);
    }

    /** Free now and not promised to a booking that starts within the next hour. */
    private List<DiningTable> freeTables() {
        Instant now = Instant.now();
        Set<Long> promised = new java.util.HashSet<>();
        for (Reservation r : reservationRepository.findByStatusInAndReservedForBetween(EnumSet.of(ReservationStatus.BOOKED), now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(1)))) {
            if (r.getTable() != null) promised.add(r.getTable().getId());
        }
        return tableRepository.findAllByOrderByNameAsc().stream()
                .filter(DiningTable::isActive)
                .filter(t -> t.getStatus() == TableStatus.AVAILABLE)
                .filter(t -> !promised.contains(t.getId()))
                .toList();
    }

    /** A quote when none is given: 0 when a fitting table is free, else when the soonest fitting table should turn, plus a few minutes for each party ahead. */
    private int estimate(int party, int ahead) {
        if (freeTables().stream().anyMatch(t -> t.getCapacity() >= party) && ahead == 0) return 0;
        int turn = settingsService.entity().getTurnMinutes();
        Instant now = Instant.now();
        long soonest = turn;
        for (DiningTable t : tableRepository.findAllByOrderByNameAsc()) {
            if (!t.isActive() || t.getStatus() == TableStatus.OUT_OF_SERVICE || t.getCapacity() < party) continue;
            if (t.getStatus() == TableStatus.AVAILABLE) { soonest = 5; break; }
            for (RestaurantOrder o : orderRepository.findByTableIdAndStatusOrderByIdAsc(t.getId(), RestaurantOrderStatus.OPEN)) {
                long left = turn - Duration.between(o.getOpenedAt(), now).toMinutes();
                soonest = Math.min(soonest, Math.max(5, left));
            }
        }
        long total = soonest + 10L * ahead;
        return (int) (Math.ceil(total / 5.0) * 5);
    }
}
