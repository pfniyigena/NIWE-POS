package com.posapp.booking;

import com.posapp.booking.dto.BookingDtos.PublicConfirmationDto;
import com.posapp.booking.dto.BookingDtos.PublicInfoDto;
import com.posapp.booking.dto.BookingRequests.PublicBookRequest;
import com.posapp.booking.dto.BookingRequests.PublicPhoneRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * The public booking page: no login. Everything under {@code /api/public/**} is opened in SecurityConfig. It never lists anyone's bookings;
 * a guest can only look up or cancel their own with the booking code plus the phone number it was made with. The module and the
 * "online booking" setting are checked by the service, which answers "not found" while either is off.
 */
@RestController
@RequestMapping("/api/public/booking")
public class PublicBookingController {

    private final AppointmentService service;

    public PublicBookingController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping("/info")
    public PublicInfoDto info() {
        return service.publicInfo();
    }

    @GetMapping("/slots")
    public List<String> slots(@RequestParam Long serviceId, @RequestParam LocalDate date, @RequestParam(required = false) Long staffId) {
        return service.publicSlots(serviceId, date, staffId);
    }

    @PostMapping("/appointments")
    public PublicConfirmationDto book(@RequestBody PublicBookRequest request) {
        return service.publicBook(request);
    }

    @PostMapping("/appointments/{code}/lookup")
    public PublicConfirmationDto lookup(@PathVariable String code, @RequestBody PublicPhoneRequest request) {
        return service.publicLookup(code, request.phone());
    }

    @PostMapping("/appointments/{code}/cancel")
    public PublicConfirmationDto cancel(@PathVariable String code, @RequestBody PublicPhoneRequest request) {
        return service.publicCancel(code, request.phone());
    }
}
