package com.posapp.restaurant;

import com.posapp.restaurant.dto.PublicReservationDto;
import com.posapp.restaurant.dto.PublicReservationRequest;
import com.posapp.restaurant.dto.PublicTableDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Fourier: "Public be able to make reservation" — the only unauthenticated endpoints in the whole
 * app, reachable with no login at all. Everything under {@code /api/public/**} is opened up in
 * {@code SecurityConfig} specifically for this; nothing else lives under that path.
 *
 * <p>Fourier's follow-up, "Public reservations should show available table for that day. The
 * person then book table. Once Booked it can not be used by anyone," is why {@link
 * #availableTables} exists alongside {@link #create}: the guest picks a real table from this
 * list, and {@code ReservationService.create}'s own {@code assertTableAvailable} re-validates
 * that choice server-side regardless of what the client sends, so a stale or tampered pick still
 * gets rejected rather than silently double-booking a table. Both endpoints are deliberately the
 * only ones here — no public GET on reservations themselves, since listing every guest's name/
 * phone/party size to any anonymous caller would leak real customer data.
 */
@RestController
@RequestMapping("/api/public/reservations")
public class PublicReservationController {

    private final ReservationService reservationService;

    public PublicReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/available-tables")
    public List<PublicTableDto> availableTables(@RequestParam LocalDate date) {
        return reservationService.availableTables(date).stream().map(PublicTableDto::from).toList();
    }

    @PostMapping
    public PublicReservationDto create(@Valid @RequestBody PublicReservationRequest request) {
        return PublicReservationDto.from(reservationService.create(request.toReservationRequest()));
    }
}
