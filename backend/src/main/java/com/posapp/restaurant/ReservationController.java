package com.posapp.restaurant;

import com.posapp.restaurant.dto.DiningTableDto;
import com.posapp.restaurant.dto.ReservationDto;
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
    public List<ReservationDto> listAll() {
        return reservationService.listAll();
    }

    /** Fourier: "show available table for that day" — same list the public booking page's picker uses, also usable from the staff-facing New Reservation form. */
    @GetMapping("/available-tables")
    public List<DiningTableDto> availableTables(@RequestParam LocalDate date) {
        return reservationService.availableTables(date);
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
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/cancel")
    public ReservationDto cancel(@PathVariable Long id) {
        return reservationService.cancel(id);
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
