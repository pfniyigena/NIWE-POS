package com.posapp.restaurant;

import com.posapp.restaurant.dto.DiningTableDto;
import com.posapp.restaurant.dto.ReservationCancelRequest;
import com.posapp.restaurant.dto.ReservationDepositRequest;
import com.posapp.restaurant.dto.ReservationDto;
import com.posapp.restaurant.dto.ReservationReminderDto;
import com.posapp.restaurant.dto.ReservationReminderRequest;
import com.posapp.restaurant.dto.ReservationSlotDto;
import com.posapp.restaurant.dto.ReservationRequest;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import com.posapp.restaurant.dto.SeatByCodeRequest;
import com.posapp.restaurant.dto.SeatReservationRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin/staff > Restaurant > Reservations — open to any logged-in user, same tier as
 * RestaurantOrderController, EXCEPT {@link #cancel} — Fourier: "the reservation can be cancelled
 * anytime by admin" — which is ADMIN-only via its own {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/restaurant/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<List<ReservationDto>> listAll(
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(reservationService.filtered(q, status, from, to), page, size);
    }

    /** Fourier: "show available table for that day". With {@code at} (and optionally {@code partySize}) only the tables free for that sitting. */
    @GetMapping("/available-tables")
    public List<DiningTableDto> availableTables(@RequestParam(required = false) LocalDate date,
                                                @RequestParam(required = false) java.time.Instant at,
                                                @RequestParam(required = false) Integer partySize) {
        return reservationService.availableTables(date, at, partySize);
    }

    /** The bookable times on a date for a party, each with how many tables it still has. */
    @GetMapping("/slots")
    public List<ReservationSlotDto> slots(@RequestParam LocalDate date, @RequestParam(required = false) Integer partySize) {
        return reservationService.slots(date, partySize);
    }

    /** Bookings coming up that still need a reminder. */
    @GetMapping("/reminders")
    public List<ReservationDto> reminders() {
        return reservationService.dueReminders();
    }

    @PostMapping
    public ReservationDto create(@Valid @RequestBody ReservationRequest request) {
        return reservationService.create(request);
    }

    @PutMapping("/{id}")
    public ReservationDto update(@PathVariable Long id, @Valid @RequestBody ReservationRequest request) {
        return reservationService.update(id, request);
    }

    /** ADMIN-only (Fourier: "the reservation can be cancelled anytime by admin"). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/cancel")
    public ReservationDto cancel(@PathVariable Long id, @RequestBody(required = false) ReservationCancelRequest request) {
        return reservationService.cancel(id, request);
    }

    /** The party did not come. Allowed once the grace period has passed; keeping a held deposit needs a manager. */
    @PostMapping("/{id}/no-show")
    public ReservationDto noShow(@PathVariable Long id) {
        return reservationService.noShow(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/mark-no-shows")
    public java.util.Map<String, Integer> markNoShows() {
        return java.util.Map.of("marked", reservationService.markNoShows());
    }

    /** Takes the deposit that was asked for when the booking was made. */
    @PostMapping("/{id}/deposit")
    public ReservationDto deposit(@PathVariable Long id, @RequestBody ReservationDepositRequest request) {
        return reservationService.collectDeposit(id, request.getMethod(), request.getAmount());
    }

    /** The reminder text (and, with {@code send}, e-mails it). */
    @PostMapping("/{id}/reminder")
    public ReservationReminderDto reminder(@PathVariable Long id, @RequestBody(required = false) ReservationReminderRequest request) {
        return reservationService.reminder(id, request != null && request.isSend());
    }

    /** The reminder was sent some other way (SMS, a call). */
    @PostMapping("/{id}/reminded")
    public ReservationDto reminded(@PathVariable Long id) {
        return reservationService.markReminded(id);
    }

    @PostMapping("/{id}/seat")
    public RestaurantOrderDto seat(@PathVariable Long id, @RequestBody(required = false) SeatReservationRequest request) {
        return reservationService.seat(id, request != null ? request : new SeatReservationRequest());
    }

    /** Staff preview before seating — looks a booking up by its "reservation number" without seating it yet. */
    @GetMapping("/lookup/{code}")
    public ReservationDto lookup(@PathVariable String code) {
        return reservationService.findByCode(code);
    }

    /** Fourier: "To use the reserved table, you need to enter reservation number." */
    @PostMapping("/seat-by-code")
    public RestaurantOrderDto seatByCode(@Valid @RequestBody SeatByCodeRequest request) {
        SeatReservationRequest seatRequest = new SeatReservationRequest();
        seatRequest.setServerId(request.getServerId());
        return reservationService.seatByCode(request.getCode(), seatRequest);
    }
}
