package com.posapp.hotel;

import com.posapp.hotel.dto.OnlineDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The manager's side of online booking: settings, which room types are offered, calendar feeds, and the e-mail runs. */
@RestController
@RequestMapping("/api/hotel")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class HotelOnlineController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HOTEL_MANAGE')";

    private final HotelOnlineService service;

    public HotelOnlineController(HotelOnlineService service) {
        this.service = service;
    }

    @GetMapping("/online/settings")
    public OnlineSettingsDto settings() {
        return service.settings();
    }

    @PutMapping("/online/settings")
    @PreAuthorize(MANAGE)
    public OnlineSettingsDto update(@RequestBody OnlineSettingsRequest request) {
        return service.update(request);
    }

    @GetMapping("/online/room-types")
    public List<OnlineRoomTypeDto> roomTypes() {
        return service.roomTypes();
    }

    @PutMapping("/online/room-types/{id}")
    @PreAuthorize(MANAGE)
    public OnlineRoomTypeDto updateRoomType(@PathVariable Long id, @RequestBody OnlineRoomTypeRequest request) {
        return service.updateRoomType(id, request);
    }

    @PostMapping("/online/room-types/{id}/feed")
    @PreAuthorize(MANAGE)
    public OnlineRoomTypeDto newFeed(@PathVariable Long id) {
        return service.newFeed(id);
    }

    @DeleteMapping("/online/room-types/{id}/feed")
    @PreAuthorize(MANAGE)
    public OnlineRoomTypeDto removeFeed(@PathVariable Long id) {
        return service.removeFeed(id);
    }

    @PostMapping("/bookings/{id}/send-confirmation")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> sendConfirmation(@PathVariable Long id) {
        service.sendConfirmationNow(id);
        return ResponseEntity.noContent().build();
    }

    /** Runs the reminder / confirmation / thank-you round now instead of waiting for the next scheduled one. */
    @PostMapping("/online/send-due")
    @PreAuthorize(MANAGE)
    public Map<String, Integer> sendDue() {
        return Map.of("sent", service.sendDueMails());
    }

    /** Releases online bookings whose deposit did not arrive in time, now instead of at the next scheduled run. */
    @PostMapping("/online/release-unpaid")
    @PreAuthorize(MANAGE)
    public Map<String, Integer> releaseUnpaid() {
        return Map.of("released", service.releaseUnpaid());
    }
}
