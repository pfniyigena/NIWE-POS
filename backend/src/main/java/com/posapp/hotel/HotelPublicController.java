package com.posapp.hotel;

import com.posapp.hotel.dto.OnlineDtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * The hotel's public booking page: no login (everything under /api/public/** is opened in SecurityConfig). A guest can only look up or cancel
 * their own booking with its code plus the phone number it was made with; the calendar feed is reached only through its secret address.
 * The module and the "online booking" switch are checked by the service, which answers "not found" while either is off.
 */
@RestController
@RequestMapping("/api/public/hotel")
public class HotelPublicController {

    private final HotelOnlineService service;

    public HotelPublicController(HotelOnlineService service) {
        this.service = service;
    }

    @GetMapping("/info")
    public PublicInfoDto info() {
        return service.info();
    }

    @GetMapping("/availability")
    public PublicAvailabilityDto availability(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) Integer adults, @RequestParam(required = false) Integer children) {
        return service.availability(from, to, adults, children);
    }

    @PostMapping("/bookings")
    public PublicBookingDto book(@RequestBody PublicBookRequest request) {
        return service.book(request);
    }

    @PostMapping("/bookings/{code}/lookup")
    public PublicBookingDto lookup(@PathVariable String code, @RequestBody PublicPhoneRequest request) {
        return service.lookup(code, request.phone());
    }

    @PostMapping("/bookings/{code}/cancel")
    public PublicBookingDto cancel(@PathVariable String code, @RequestBody PublicPhoneRequest request) {
        return service.cancel(code, request.phone());
    }

    @GetMapping("/ical/{token}.ics")
    public ResponseEntity<String> ical(@PathVariable String token) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")).header("Cache-Control", "no-store").body(service.ical(token));
    }
}
