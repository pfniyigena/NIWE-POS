package com.posapp.restaurant;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.restaurant.dto.DiningTableDto;
import com.posapp.restaurant.dto.OpenTableOrderRequest;
import com.posapp.restaurant.dto.ReservationDto;
import com.posapp.restaurant.dto.ReservationRequest;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import com.posapp.restaurant.dto.SeatReservationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Admin/staff > Restaurant > Reservations (Fourier: "Reservations"). Free-text guest details, not
 * tied to an existing Customer record — see Reservation's own class javadoc.
 *
 * <p>Fourier: "Public reservations should show available table for that day. The person then
 * book table. Once Booked it can not be used by anyone. To use the reserved table, you need to
 * enter reservation number. But the reservation can be cancelled anytime by admin." See {@link
 * #availableTables}, {@link #assertTableAvailable}, {@link #seatByCode}, and {@code
 * ReservationController.cancel}'s {@code @PreAuthorize} respectively.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final DiningTableRepository tableRepository;
    private final RestaurantOrderService orderService;
    private final AuditLogService auditLogService;

    public ReservationService(ReservationRepository reservationRepository, DiningTableRepository tableRepository,
                               RestaurantOrderService orderService,
                               AuditLogService auditLogService) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.orderService = orderService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ReservationDto> listAll() {
        return reservationRepository.findAllByOrderByReservedForAsc().stream().map(ReservationDto::from).toList();
    }

    /**
     * Fourier: "Public reservations should show available table for that day." Every active,
     * in-service table that does NOT already have a BOOKED reservation somewhere in the given
     * calendar day (UTC) — the same exclusivity {@link #assertTableAvailable} enforces at booking
     * time, exposed here so a caller (the public booking page, or staff) can see it up front
     * instead of guessing and getting a conflict back.
     */
    @Transactional(readOnly = true)
    public List<DiningTableDto> availableTables(LocalDate date) {
        if (date == null) {
            throw ApiException.badRequest("A date is required");
        }
        var reservedTableIds = reservationRepository
                .findByStatusAndReservedForBetween(ReservationStatus.BOOKED, dayStart(date), dayEnd(date)).stream()
                .map(r -> r.getTable() != null ? r.getTable().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return tableRepository.findAllByOrderByNameAsc().stream()
                .filter(DiningTable::isActive)
                .filter(t -> t.getStatus() != TableStatus.OUT_OF_SERVICE)
                .filter(t -> !reservedTableIds.contains(t.getId()))
                .map(t -> DiningTableDto.from(t, null))
                .toList();
    }

    @Transactional
    public ReservationDto create(ReservationRequest request) {
        if (request.getTableId() != null) {
            assertTableAvailable(request.getTableId(), request.getReservedFor(), null);
        }
        Reservation reservation = new Reservation();
        reservation.setConfirmationCode(generateConfirmationCode());
        applyRequest(reservation, request);
        Reservation saved = reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_CREATED",
                "Booked a table for " + saved.getGuestName() + " (" + saved.getPartySize() + ") — confirmation " + saved.getConfirmationCode(),
                "Reservation", saved.getId());
        return ReservationDto.from(saved);
    }

    @Transactional
    public ReservationDto update(Long id, ReservationRequest request) {
        Reservation reservation = getOrThrow(id);
        if (request.getTableId() != null) {
            assertTableAvailable(request.getTableId(), request.getReservedFor(), id);
        }
        applyRequest(reservation, request);
        Reservation saved = reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_UPDATED", "Updated reservation for " + saved.getGuestName(), "Reservation", saved.getId());
        return ReservationDto.from(saved);
    }

    /** Staff preview before seating — see {@link #seatByCode}. */
    @Transactional(readOnly = true)
    public ReservationDto findByCode(String code) {
        return ReservationDto.from(getByCodeOrThrow(code));
    }

    /**
     * Fourier: "To use the reserved table, you need to enter reservation number." The staff-facing
     * counterpart of {@link #seat} that looks the booking up by its {@code confirmationCode}
     * instead of an internal id, since that's the only thing the guest actually has. Delegates
     * straight to {@link #seat} once found — same table/order-opening logic either way.
     */
    @Transactional
    public RestaurantOrderDto seatByCode(String code, SeatReservationRequest request) {
        Reservation reservation = getByCodeOrThrow(code);
        return seat(reservation.getId(), request);
    }

    @Transactional
    public ReservationDto cancel(Long id) {
        Reservation reservation = getOrThrow(id);
        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_CANCELLED", "Cancelled reservation for " + saved.getGuestName(), "Reservation", saved.getId());
        return ReservationDto.from(saved);
    }

    /**
     * Turns a booking into a real, open {@link RestaurantOrder} the moment the party actually
     * arrives — reuses {@link RestaurantOrderService#openOrder} so a seated reservation behaves
     * exactly like any other newly-opened table order from that point on (same kitchen/checkout
     * flow), rather than this service duplicating that logic.
     */
    @Transactional
    public RestaurantOrderDto seat(Long id, SeatReservationRequest request) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw ApiException.badRequest("This reservation is already " + reservation.getStatus());
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
        reservationRepository.save(reservation);
        auditLogService.record("RESTAURANT_RESERVATION_SEATED", "Seated reservation for " + reservation.getGuestName(), "Reservation", reservation.getId());
        return order;
    }

    private void applyRequest(Reservation reservation, ReservationRequest request) {
        reservation.setGuestName(request.getGuestName());
        reservation.setGuestPhone(request.getGuestPhone());
        reservation.setPartySize(request.getPartySize() > 0 ? request.getPartySize() : 1);
        reservation.setReservedFor(request.getReservedFor());
        reservation.setNotes(request.getNotes());
        if (request.getTableId() != null) {
            reservation.setTable(tableRepository.findById(request.getTableId())
                    .orElseThrow(() -> ApiException.notFound("Table not found: " + request.getTableId())));
        } else {
            reservation.setTable(null);
        }
    }

    private Reservation getOrThrow(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Reservation not found: " + id));
    }

    private Reservation getByCodeOrThrow(String code) {
        return reservationRepository.findByConfirmationCode(code)
                .orElseThrow(() -> ApiException.notFound("No reservation found with number: " + code));
    }

    /**
     * Fourier: "Once Booked it can not be used by anyone." One BOOKED reservation per table per
     * calendar day (UTC) — deliberately day-granular rather than a real time-window overlap check,
     * matching how the public booking page itself only ever asks "which tables are free that day."
     * {@code excludeReservationId} lets {@link #update} re-check a reservation against every OTHER
     * booking without tripping over itself.
     */
    private void assertTableAvailable(Long tableId, Instant reservedFor, Long excludeReservationId) {
        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> ApiException.notFound("Table not found: " + tableId));
        if (!table.isActive() || table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw ApiException.badRequest("Table '" + table.getName() + "' isn't available for reservations");
        }
        if (reservedFor == null) {
            return;
        }
        LocalDate date = reservedFor.atZone(ZoneOffset.UTC).toLocalDate();
        boolean taken = reservationRepository.findByStatusAndReservedForBetween(ReservationStatus.BOOKED, dayStart(date), dayEnd(date)).stream()
                .anyMatch(r -> !r.getId().equals(excludeReservationId) && r.getTable() != null && r.getTable().getId().equals(tableId));
        if (taken) {
            throw ApiException.conflict("Table '" + table.getName() + "' is already reserved for that day");
        }
    }

    /** Same day-boundary convention ReportController's own start()/end() helpers use — a UTC calendar day. */
    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant dayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    }

    /** "Reservation number" — short, guest-readable, unique. Regenerated on collision (astronomically rare at this id space, but checked anyway). */
    private String generateConfirmationCode() {
        String code;
        do {
            code = "RES-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        } while (reservationRepository.existsByConfirmationCode(code));
        return code;
    }
}
