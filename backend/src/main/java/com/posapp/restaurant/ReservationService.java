package com.posapp.restaurant;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.hotel.HotelClock;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.restaurant.dto.DiningTableDto;
import com.posapp.restaurant.dto.OpenTableOrderRequest;
import com.posapp.restaurant.dto.ReservationCancelRequest;
import com.posapp.restaurant.dto.ReservationDto;
import com.posapp.restaurant.dto.ReservationInfoDto;
import com.posapp.restaurant.dto.ReservationReminderDto;
import com.posapp.restaurant.dto.ReservationRequest;
import com.posapp.restaurant.dto.ReservationSlotDto;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import com.posapp.restaurant.dto.SeatReservationRequest;
import com.posapp.settings.StoreSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Admin/staff > Restaurant > Reservations. Free-text guest details, not tied to a Customer record.
 *
 * <p>Fourier: "Public reservations should show available table for that day. The person then book
 * table. Once Booked it can not be used by anyone. To use the reserved table, you need to enter
 * reservation number. But the reservation can be cancelled anytime by admin."
 *
 * <p>A booking holds its table for a time window — from the booked time for the booking's own
 * duration, or the restaurant's usual turn time — so one table can take an early and a late
 * sitting on the same day, but never two parties at once. Opening hours, the booking horizon,
 * deposits, reminders and the no-show rules come from the restaurant settings.
 */
@Service
public class ReservationService {

    public static final Set<String> DEPOSIT_STATES = Set.of("NONE", "REQUIRED", "HELD", "APPLIED", "REFUNDED", "FORFEITED");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final ReservationRepository reservationRepository;
    private final DiningTableRepository tableRepository;
    private final RestaurantOrderService orderService;
    private final RestaurantOrderRepository orderRepository;
    private final AuditLogService auditLogService;
    private final RestaurantSettingsService settingsService;
    private final HotelClock clock;
    private final AccountingService accountingService;
    private final PaymentMethodService paymentMethodService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final RestaurantCashService cashService;

    public ReservationService(ReservationRepository reservationRepository, DiningTableRepository tableRepository,
                               RestaurantOrderService orderService, RestaurantOrderRepository orderRepository,
                               AuditLogService auditLogService, RestaurantSettingsService settingsService, HotelClock clock,
                               AccountingService accountingService, PaymentMethodService paymentMethodService,
                               MailService mailService, StoreSettingsService storeSettingsService, RestaurantCashService cashService) {
        this.cashService = cashService;
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
        this.settingsService = settingsService;
        this.clock = clock;
        this.accountingService = accountingService;
        this.paymentMethodService = paymentMethodService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<ReservationDto> filtered(String q, String status, LocalDate from, LocalDate to) {
        List<Reservation> rows = reservationRepository.findAllByOrderByReservedForAsc().stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().name().equalsIgnoreCase(status.trim()))
                .filter(r -> com.posapp.common.ListPaging.between(r.getReservedFor(), from, to))
                .filter(r -> com.posapp.common.ListPaging.has(q, r.getGuestName(), r.getGuestPhone(), r.getGuestEmail(), r.getConfirmationCode(),
                        r.getTable() != null ? r.getTable().getName() : null, r.getNotes()))
                .toList();
        return dtos(rows);
    }

    @Transactional(readOnly = true)
    public List<ReservationDto> listAll() {
        return dtos(reservationRepository.findAllByOrderByReservedForAsc());
    }

    private List<ReservationDto> dtos(List<Reservation> rows) {
        int turn = settings().getTurnMinutes();
        Map<String, int[]> history = new HashMap<>();
        for (Reservation r : reservationRepository.findAll()) {
            String key = digits(r.getGuestPhone());
            if (key == null) continue;
            int[] h = history.computeIfAbsent(key, k -> new int[2]);
            if (r.getStatus() == ReservationStatus.NO_SHOW) h[0]++;
            if (r.getStatus() == ReservationStatus.SEATED) h[1]++;
        }
        List<ReservationDto> out = new ArrayList<>();
        for (Reservation r : rows) {
            int[] h = history.getOrDefault(digits(r.getGuestPhone()) == null ? "" : digits(r.getGuestPhone()), new int[2]);
            out.add(ReservationDto.from(r, turn, h[0], h[1]));
        }
        return out;
    }

    private ReservationDto dto(Reservation r) {
        return dtos(List.of(r)).get(0);
    }

    private static String digits(String phone) {
        if (phone == null) return null;
        String d = phone.replaceAll("\\D", "");
        if (d.isEmpty()) return null;
        // compare the last nine digits so +250 788 400 001 and 0788 400 001 are the same guest
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    @Transactional(readOnly = true)
    public ReservationInfoDto info() {
        RestaurantSettings s = settings();
        String name = storeSettingsService.getSettingsEntity().getBusinessName();
        return new ReservationInfoDto(name == null || name.isBlank() ? "the restaurant" : name, s.getOpenTime(), s.getCloseTime(), s.getSlotMinutes(),
                s.getTurnMinutes(), s.getBookingDaysAhead(), s.getDepositPerGuest(), clock.today().toString(), zone().getId());
    }

    // ------------------------------------------------------------------ availability

    /** The times a party of this size could be seated on a date, with how many tables each time still has. Times already past are left out. */
    @Transactional(readOnly = true)
    public List<ReservationSlotDto> slots(LocalDate date, Integer partySize) {
        if (date == null) throw ApiException.badRequest("A date is required");
        int party = partySize == null || partySize < 1 ? 1 : partySize;
        RestaurantSettings s = settings();
        Instant now = Instant.now();
        List<ReservationSlotDto> out = new ArrayList<>();
        for (Instant start : grid(date, s)) {
            if (!start.isAfter(now)) continue;
            int free = freeTables(start, minutes(null, s), party, null).size();
            out.add(new ReservationSlotDto(start, start.atZone(zone()).format(HHMM), free));
        }
        return out;
    }

    /**
     * Tables that can be booked. With {@code at} given: those free for the party at that time. Without it: those that are
     * free at some bookable time that day (the same exclusivity {@link #assertTableAvailable} enforces at booking time).
     */
    @Transactional(readOnly = true)
    public List<DiningTableDto> availableTables(LocalDate date, Instant at, Integer partySize) {
        int party = partySize == null || partySize < 1 ? 1 : partySize;
        RestaurantSettings s = settings();
        if (at != null) {
            return freeTables(at, minutes(null, s), party, null).stream().map(t -> DiningTableDto.from(t, null)).toList();
        }
        if (date == null) throw ApiException.badRequest("A date is required");
        java.util.LinkedHashMap<Long, DiningTable> any = new java.util.LinkedHashMap<>();
        for (Instant start : grid(date, s)) {
            for (DiningTable t : freeTables(start, minutes(null, s), party, null)) any.putIfAbsent(t.getId(), t);
        }
        return any.values().stream().sorted(java.util.Comparator.comparing(DiningTable::getName)).map(t -> DiningTableDto.from(t, null)).toList();
    }

    private List<Instant> grid(LocalDate date, RestaurantSettings s) {
        List<Instant> out = new ArrayList<>();
        LocalTime open = LocalTime.parse(s.getOpenTime());
        LocalTime close = LocalTime.parse(s.getCloseTime());
        for (LocalTime t = open; t.isBefore(close); t = t.plusMinutes(s.getSlotMinutes())) {
            out.add(date.atTime(t).atZone(zone()).toInstant());
            if (t.plusMinutes(s.getSlotMinutes()).isBefore(t)) break; // wrapped past midnight
        }
        return out;
    }

    private List<DiningTable> freeTables(Instant start, int minutes, int party, Long excludeReservationId) {
        Instant end = start.plusSeconds(minutes * 60L);
        Set<Long> busy = new java.util.HashSet<>();
        for (Reservation r : blockers(start, end, excludeReservationId)) busy.add(r.getTable().getId());
        return tableRepository.findAllByOrderByNameAsc().stream()
                .filter(DiningTable::isActive)
                .filter(t -> t.getStatus() != TableStatus.OUT_OF_SERVICE)
                .filter(t -> t.getCapacity() >= party)
                .filter(t -> !busy.contains(t.getId()))
                .toList();
    }

    /** Bookings that hold a table during the window: every BOOKED one, and a SEATED one while its bill is still open. */
    private List<Reservation> blockers(Instant start, Instant end, Long excludeReservationId) {
        int turn = settings().getTurnMinutes();
        List<Reservation> out = new ArrayList<>();
        for (Reservation r : reservationRepository.findByStatusInAndReservedForBetween(
                EnumSet.of(ReservationStatus.BOOKED, ReservationStatus.SEATED), start.minus(Duration.ofHours(12)), end)) {
            if (r.getTable() == null || r.getId().equals(excludeReservationId)) continue;
            if (r.getStatus() == ReservationStatus.SEATED) {
                boolean open = r.getRestaurantOrderId() != null && orderRepository.findById(r.getRestaurantOrderId())
                        .map(o -> o.getStatus() == RestaurantOrderStatus.OPEN).orElse(false);
                if (!open) continue;
            }
            Instant rEnd = r.getReservedFor().plusSeconds(minutes(r.getDurationMinutes(), turn) * 60L);
            if (r.getReservedFor().isBefore(end) && rEnd.isAfter(start)) out.add(r);
        }
        return out;
    }

    private int minutes(Integer own, RestaurantSettings s) {
        return minutes(own, s.getTurnMinutes());
    }

    private static int minutes(Integer own, int turn) {
        return own != null ? own : turn;
    }

    /**
     * Fourier: "Once Booked it can not be used by anyone." The table must be active and in service, and free for the whole of
     * the booking's time window. {@code excludeReservationId} lets an update re-check against every OTHER booking.
     */
    private void assertTableAvailable(Long tableId, Instant reservedFor, Integer durationMinutes, Long excludeReservationId) {
        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> ApiException.notFound("Table not found: " + tableId));
        if (!table.isActive() || table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw ApiException.badRequest("Table '" + table.getName() + "' isn't available for reservations");
        }
        if (reservedFor == null) return;
        int minutes = minutes(durationMinutes, settings());
        Instant end = reservedFor.plusSeconds(minutes * 60L);
        for (Reservation r : blockers(reservedFor, end, excludeReservationId)) {
            if (r.getTable().getId().equals(tableId)) {
                int rm = minutes(r.getDurationMinutes(), settings());
                throw ApiException.conflict("Table '" + table.getName() + "' is already reserved from "
                        + r.getReservedFor().atZone(zone()).format(HHMM) + " to " + r.getReservedFor().plusSeconds(rm * 60L).atZone(zone()).format(HHMM) + " that day");
            }
        }
    }

    // ------------------------------------------------------------------ booking

    @Transactional
    public ReservationDto create(ReservationRequest request) {
        return book(request, "STAFF");
    }

    /** A booking made by a guest on the public page: the hours, the horizon, the table size and the no-show rule all apply. */
    @Transactional
    public ReservationDto createOnline(ReservationRequest request) {
        RestaurantSettings s = settings();
        if (request.getReservedFor() == null || !request.getReservedFor().isAfter(Instant.now())) {
            throw ApiException.badRequest("Choose a time in the future");
        }
        var local = request.getReservedFor().atZone(zone());
        LocalDate today = clock.today();
        if (local.toLocalDate().isAfter(today.plusDays(s.getBookingDaysAhead()))) {
            throw ApiException.badRequest("Bookings open up to " + s.getBookingDaysAhead() + " days ahead");
        }
        LocalTime open = LocalTime.parse(s.getOpenTime());
        LocalTime close = LocalTime.parse(s.getCloseTime());
        LocalTime time = local.toLocalTime();
        long sinceOpen = Duration.between(open, time).toMinutes();
        if (time.isBefore(open) || !time.isBefore(close)) {
            throw ApiException.badRequest("We take bookings between " + s.getOpenTime() + " and " + s.getCloseTime());
        }
        if (sinceOpen % s.getSlotMinutes() != 0 || local.getSecond() != 0) {
            throw ApiException.badRequest("Choose one of the times offered (every " + s.getSlotMinutes() + " minutes from " + s.getOpenTime() + ")");
        }
        if (request.getPartySize() < 1 || request.getPartySize() > 50) {
            throw ApiException.badRequest("Party size must be between 1 and 50");
        }
        if (request.getTableId() != null) {
            DiningTable t = tableRepository.findById(request.getTableId()).orElseThrow(() -> ApiException.notFound("Table not found: " + request.getTableId()));
            if (request.getPartySize() > t.getCapacity()) {
                throw ApiException.badRequest("Table '" + t.getName() + "' seats at most " + t.getCapacity());
            }
        }
        if (s.getNoShowBlockCount() > 0) {
            String key = digits(request.getGuestPhone());
            long misses = key == null ? 0 : reservationRepository.findAll().stream()
                    .filter(r -> r.getStatus() == ReservationStatus.NO_SHOW && key.equals(digits(r.getGuestPhone()))).count();
            if (misses >= s.getNoShowBlockCount()) {
                throw ApiException.badRequest("We cannot take an online booking for this phone number. Please call the restaurant.");
            }
        }
        request.setDurationMinutes(null);
        request.setDepositMethod(null);
        request.setDepositAmount(null);
        return book(request, "ONLINE");
    }

    private ReservationDto book(ReservationRequest request, String source) {
        RestaurantSettings s = settings();
        validate(request);
        if (request.getTableId() != null) {
            assertTableAvailable(request.getTableId(), request.getReservedFor(), request.getDurationMinutes(), null);
        }
        Reservation reservation = new Reservation();
        reservation.setConfirmationCode(generateConfirmationCode());
        reservation.setSource(source);
        applyRequest(reservation, request);
        BigDecimal deposit = depositFor(request.getDepositAmount(), reservation.getPartySize(), s);
        reservation.setDepositAmount(deposit);
        reservation.setDepositStatus(deposit.signum() > 0 ? "REQUIRED" : "NONE");
        Reservation saved = reservationRepository.save(reservation);
        if (deposit.signum() > 0 && request.getDepositMethod() != null && !request.getDepositMethod().isBlank()) {
            takeDeposit(saved, request.getDepositMethod(), null);
        }
        auditLogService.record("RESTAURANT_RESERVATION_CREATED",
                "Booked a table for " + saved.getGuestName() + " (" + saved.getPartySize() + ") — confirmation " + saved.getConfirmationCode(),
                "Reservation", saved.getId());
        return dto(saved);
    }

    @Transactional
    public ReservationDto update(Long id, ReservationRequest request) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + pretty(reservation.getStatus()) + " and cannot be changed");
        }
        validate(request);
        if (request.getTableId() != null) {
            assertTableAvailable(request.getTableId(), request.getReservedFor(), request.getDurationMinutes(), id);
        }
        Instant before = reservation.getReservedFor();
        applyRequest(reservation, request);
        if (before != null && !before.equals(reservation.getReservedFor())) reservation.setRemindedAt(null);
        // the deposit can only be re-worked while it has not been paid
        if ("NONE".equals(reservation.getDepositStatus()) || "REQUIRED".equals(reservation.getDepositStatus())) {
            BigDecimal deposit = depositFor(request.getDepositAmount(), reservation.getPartySize(), settings());
            reservation.setDepositAmount(deposit);
            reservation.setDepositStatus(deposit.signum() > 0 ? "REQUIRED" : "NONE");
        }
        Reservation saved = reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_UPDATED", "Updated reservation for " + saved.getGuestName(), "Reservation", saved.getId());
        return dto(saved);
    }

    private void validate(ReservationRequest request) {
        if (request.getGuestEmail() != null && !request.getGuestEmail().isBlank()) {
            String e = request.getGuestEmail().trim();
            if (e.length() > 160 || !e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw ApiException.badRequest("That e-mail address does not look right");
        }
        if (request.getDurationMinutes() != null && (request.getDurationMinutes() < 15 || request.getDurationMinutes() > 480)) {
            throw ApiException.badRequest("The table time must be between 15 and 480 minutes");
        }
        if (request.getDepositAmount() != null && (request.getDepositAmount().signum() < 0 || request.getDepositAmount().compareTo(BigDecimal.valueOf(100_000_000)) > 0)) {
            throw ApiException.badRequest("The deposit must be between 0 and 100,000,000");
        }
        if (request.getPartySize() > 500) throw ApiException.badRequest("Party size is too large");
    }

    private BigDecimal depositFor(BigDecimal asked, int party, RestaurantSettings s) {
        BigDecimal v = asked != null ? asked : s.getDepositPerGuest().multiply(BigDecimal.valueOf(party));
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private void applyRequest(Reservation reservation, ReservationRequest request) {
        reservation.setGuestName(request.getGuestName().trim());
        reservation.setGuestPhone(request.getGuestPhone());
        reservation.setGuestEmail(request.getGuestEmail() == null || request.getGuestEmail().isBlank() ? null : request.getGuestEmail().trim());
        reservation.setPartySize(request.getPartySize() > 0 ? request.getPartySize() : 1);
        reservation.setReservedFor(request.getReservedFor());
        reservation.setDurationMinutes(request.getDurationMinutes());
        reservation.setNotes(request.getNotes());
        if (request.getTableId() != null) {
            reservation.setTable(tableRepository.findById(request.getTableId())
                    .orElseThrow(() -> ApiException.notFound("Table not found: " + request.getTableId())));
        } else {
            reservation.setTable(null);
        }
    }

    // ------------------------------------------------------------------ seating

    /** Staff preview before seating — see {@link #seatByCode}. */
    @Transactional(readOnly = true)
    public ReservationDto findByCode(String code) {
        return dto(getByCodeOrThrow(code));
    }

    /** Fourier: "To use the reserved table, you need to enter reservation number." */
    @Transactional
    public RestaurantOrderDto seatByCode(String code, SeatReservationRequest request) {
        Reservation reservation = getByCodeOrThrow(code);
        return seat(reservation.getId(), request);
    }

    /**
     * Turns a booking into a real, open {@link RestaurantOrder} the moment the party arrives. A deposit that was taken goes onto
     * the bill; one that was never paid is dropped.
     */
    @Transactional
    public RestaurantOrderDto seat(Long id, SeatReservationRequest request) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + pretty(reservation.getStatus()));
        }
        Long tableId = request.getTableId() != null ? request.getTableId()
                : (reservation.getTable() != null ? reservation.getTable().getId() : null);
        if (tableId == null) {
            throw ApiException.badRequest("Assign a table before seating this reservation");
        }
        if (reservation.getTable() == null || !reservation.getTable().getId().equals(tableId)) {
            reservation.setTable(tableRepository.findById(tableId).orElseThrow(() -> ApiException.notFound("Table not found: " + tableId)));
        }
        OpenTableOrderRequest openRequest = new OpenTableOrderRequest();
        openRequest.setGuestCount(reservation.getPartySize());
        openRequest.setServerId(request.getServerId());
        openRequest.setNotes("Seated from reservation for " + reservation.getGuestName());
        RestaurantOrderDto order = orderService.openOrder(tableId, openRequest);
        reservation.setStatus(ReservationStatus.SEATED);
        reservation.setRestaurantOrderId(order.id());
        if ("HELD".equals(reservation.getDepositStatus())) {
            RestaurantOrder entity = orderRepository.findById(order.id()).orElseThrow();
            entity.setDepositApplied(reservation.getDepositAmount());
            entity.setDepositReservationId(reservation.getId());
            orderRepository.save(entity);
            reservation.setDepositStatus("APPLIED");
            order = RestaurantOrderDto.from(entity);
        } else if ("REQUIRED".equals(reservation.getDepositStatus())) {
            reservation.setDepositStatus("NONE");
        }
        reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_SEATED", "Seated reservation for " + reservation.getGuestName(), "Reservation", reservation.getId());
        return order;
    }

    // ------------------------------------------------------------------ cancel, no-show

    @Transactional
    public ReservationDto cancel(Long id, ReservationCancelRequest request) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + pretty(reservation.getStatus()));
        }
        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() > 200) throw ApiException.badRequest("The reason is too long (200 characters at most)");
        String choice = request == null || request.getDeposit() == null ? "" : request.getDeposit().trim().toUpperCase();
        if (!choice.isEmpty() && !choice.equals("REFUND") && !choice.equals("FORFEIT")) {
            throw ApiException.badRequest("Deposit must be REFUND or FORFEIT");
        }
        String note = "";
        if ("HELD".equals(reservation.getDepositStatus())) {
            if (choice.isEmpty()) {
                long hoursAhead = Duration.between(Instant.now(), reservation.getReservedFor()).toHours();
                choice = hoursAhead >= settings().getDepositRefundHours() ? "REFUND" : "FORFEIT";
            }
            settleDeposit(reservation, choice.equals("REFUND") ? "REFUNDED" : "FORFEITED");
            note = choice.equals("REFUND") ? " (deposit refunded)" : " (deposit kept)";
        } else if ("REQUIRED".equals(reservation.getDepositStatus())) {
            reservation.setDepositStatus("NONE");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelReason(reason.isEmpty() ? null : reason);
        Reservation saved = reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_CANCELLED", "Cancelled reservation for " + saved.getGuestName() + note, "Reservation", saved.getId());
        return dto(saved);
    }

    /** The party did not arrive. Allowed once the grace period after the booked time has passed; a held deposit is kept, which needs a manager. */
    @Transactional
    public ReservationDto noShow(Long id) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + pretty(reservation.getStatus()));
        }
        Instant after = reservation.getReservedFor().plusSeconds(settings().getGraceMinutes() * 60L);
        if (Instant.now().isBefore(after)) {
            throw ApiException.badRequest("Wait until " + after.atZone(zone()).format(HHMM) + " before marking a no-show");
        }
        if ("HELD".equals(reservation.getDepositStatus()) && !RestaurantAccess.isApprover()) {
            throw ApiException.forbidden("Keeping a deposit needs a manager's approval");
        }
        return dto(markNoShow(reservation));
    }

    /** Marks every booking whose grace period is over as a no-show. Manager action. */
    @Transactional
    public int markNoShows() {
        Instant now = Instant.now();
        int n = 0;
        for (Reservation r : reservationRepository.findByStatus(ReservationStatus.BOOKED)) {
            if (now.isBefore(r.getReservedFor().plusSeconds(settings().getGraceMinutes() * 60L))) continue;
            markNoShow(r);
            n++;
        }
        return n;
    }

    private Reservation markNoShow(Reservation reservation) {
        String note = "";
        if ("HELD".equals(reservation.getDepositStatus())) {
            settleDeposit(reservation, "FORFEITED");
            note = " (deposit kept)";
        } else if ("REQUIRED".equals(reservation.getDepositStatus())) {
            reservation.setDepositStatus("NONE");
        }
        reservation.setStatus(ReservationStatus.NO_SHOW);
        reservation.setNoShowAt(Instant.now());
        Reservation saved = reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_NO_SHOW", "No-show: reservation for " + saved.getGuestName() + note, "Reservation", saved.getId());
        return saved;
    }

    // ------------------------------------------------------------------ deposits

    @Transactional
    public ReservationDto collectDeposit(Long id, String method, BigDecimal amount) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + pretty(reservation.getStatus()));
        }
        if (!"REQUIRED".equals(reservation.getDepositStatus())) {
            throw ApiException.badRequest("There is no deposit waiting to be paid on this reservation");
        }
        if (method == null || method.isBlank()) throw ApiException.badRequest("Choose how the deposit is paid");
        takeDeposit(reservation, method, amount);
        return dto(reservationRepository.save(reservation));
    }

    private void takeDeposit(Reservation reservation, String method, BigDecimal amount) {
        String code = paymentMethodService.validateActiveCodeOrThrow(method);
        if (amount != null) {
            if (amount.signum() <= 0 || amount.compareTo(BigDecimal.valueOf(100_000_000)) > 0) throw ApiException.badRequest("The deposit must be more than zero");
            reservation.setDepositAmount(amount.setScale(2, RoundingMode.HALF_UP));
        }
        reservation.setDepositMethod(code);
        reservation.setDepositStatus("HELD");
        accountingService.postRestaurantDeposit("RECEIVED", reservation.getId(), reservation.getConfirmationCode(), reservation.getDepositAmount());
        cashService.deposit("DEPOSIT_RECEIVED", reservation.getId(), reservation.getConfirmationCode(), code, reservation.getDepositAmount());
        auditLogService.record("RESTAURANT_DEPOSIT_RECEIVED", "Took a deposit of " + reservation.getDepositAmount() + " for " + reservation.getGuestName(), "Reservation", reservation.getId());
    }

    private void settleDeposit(Reservation reservation, String state) {
        accountingService.postRestaurantDeposit(state.equals("REFUNDED") ? "REFUNDED" : "FORFEITED", reservation.getId(),
                reservation.getConfirmationCode(), reservation.getDepositAmount());
        cashService.deposit(state.equals("REFUNDED") ? "DEPOSIT_REFUNDED" : "DEPOSIT_FORFEITED", reservation.getId(), reservation.getConfirmationCode(),
                reservation.getDepositMethod(), reservation.getDepositAmount());
        reservation.setDepositStatus(state);
        auditLogService.record(state.equals("REFUNDED") ? "RESTAURANT_DEPOSIT_REFUNDED" : "RESTAURANT_DEPOSIT_FORFEITED",
                (state.equals("REFUNDED") ? "Refunded" : "Kept") + " the deposit of " + reservation.getDepositAmount() + " for " + reservation.getGuestName(),
                "Reservation", reservation.getId());
    }

    // ------------------------------------------------------------------ reminders

    /** Booked parties coming up within the reminder window who have not been reminded yet. */
    @Transactional(readOnly = true)
    public List<ReservationDto> dueReminders() {
        int hours = settings().getReminderHours();
        if (hours <= 0) return List.of();
        Instant now = Instant.now();
        Instant limit = now.plus(Duration.ofHours(hours));
        return dtos(reservationRepository.findByStatus(ReservationStatus.BOOKED).stream()
                .filter(r -> r.getRemindedAt() == null && r.getReservedFor().isAfter(now) && !r.getReservedFor().isAfter(limit))
                .sorted(java.util.Comparator.comparing(Reservation::getReservedFor)).toList());
    }

    @Transactional
    public ReservationReminderDto reminder(Long id, boolean send) {
        Reservation r = getOrThrow(id);
        if (r.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + pretty(r.getStatus()));
        }
        String text = reminderText(r);
        if (!send) return new ReservationReminderDto(text, false, null);
        if (r.getGuestEmail() == null) {
            return new ReservationReminderDto(text, false, "No e-mail address on this booking — copy the message and send it by SMS or WhatsApp, then mark it as sent.");
        }
        if (!mailService.isConfigured()) {
            return new ReservationReminderDto(text, false, "E-mail is not set up yet (Admin > Settings > Email).");
        }
        try {
            mailService.send(r.getGuestEmail(), "Your table reservation " + r.getConfirmationCode(), "<p>" + escape(text) + "</p>", List.of());
        } catch (ApiException e) {
            return new ReservationReminderDto(text, false, e.getMessage());
        }
        r.setRemindedAt(Instant.now());
        reservationRepository.save(r);
        auditLogService.record("RESTAURANT_RESERVATION_REMINDED", "E-mailed a reminder to " + r.getGuestName(), "Reservation", r.getId());
        return new ReservationReminderDto(text, true, "Reminder e-mailed to " + r.getGuestEmail());
    }

    @Transactional
    public ReservationDto markReminded(Long id) {
        Reservation r = getOrThrow(id);
        if (r.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + pretty(r.getStatus()));
        }
        r.setRemindedAt(Instant.now());
        auditLogService.record("RESTAURANT_RESERVATION_REMINDED", "Marked the reminder to " + r.getGuestName() + " as sent", "Reservation", r.getId());
        return dto(reservationRepository.save(r));
    }

    /** Used by the reminder job: e-mails every due reminder that has an address. Returns how many went out. */
    @Transactional
    public int sendDueReminders() {
        if (!mailService.isConfigured()) return 0;
        int sent = 0;
        for (ReservationDto d : dueReminders()) {
            if (d.guestEmail() == null) continue;
            if (reminder(d.id(), true).emailed()) sent++;
        }
        return sent;
    }

    private String reminderText(Reservation r) {
        var local = r.getReservedFor().atZone(zone());
        String name = storeSettingsService.getSettingsEntity().getBusinessName();
        return settings().getReminderTemplate()
                .replace("{name}", r.getGuestName())
                .replace("{party}", String.valueOf(r.getPartySize()))
                .replace("{restaurant}", name == null || name.isBlank() ? "the restaurant" : name)
                .replace("{date}", local.format(DATE))
                .replace("{time}", local.format(HHMM))
                .replace("{code}", r.getConfirmationCode());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------ helpers

    private RestaurantSettings settings() {
        return settingsService.entity();
    }

    private ZoneId zone() {
        return clock.zone();
    }

    private static String pretty(ReservationStatus s) {
        return s.name().toLowerCase().replace('_', ' ');
    }

    private Reservation getOrThrow(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Reservation not found: " + id));
    }

    private Reservation getByCodeOrThrow(String code) {
        return reservationRepository.findByConfirmationCode(code)
                .orElseThrow(() -> ApiException.notFound("No reservation found with number: " + code));
    }

    /** "Reservation number" — short, guest-readable, unique. */
    private String generateConfirmationCode() {
        String code;
        do {
            code = "RES-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        } while (reservationRepository.existsByConfirmationCode(code));
        return code;
    }
}
