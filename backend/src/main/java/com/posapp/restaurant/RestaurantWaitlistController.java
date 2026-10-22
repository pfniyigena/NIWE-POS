package com.posapp.restaurant;

import com.posapp.restaurant.dto.RestaurantOrderDto;
import com.posapp.restaurant.dto.RestaurantWaitlistDto;
import com.posapp.restaurant.dto.RestaurantWaitlistRequest;
import com.posapp.restaurant.dto.SeatReservationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Restaurant > Waiting list. Open to any signed-in user, like the order screens. */
@RestController
@RequestMapping("/api/restaurant/waitlist")
public class RestaurantWaitlistController {

    private final RestaurantWaitlistService service;

    public RestaurantWaitlistController(RestaurantWaitlistService service) {
        this.service = service;
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<List<RestaurantWaitlistDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(service.list(q, status), page, size);
    }

    @PostMapping
    public RestaurantWaitlistDto add(@Valid @RequestBody RestaurantWaitlistRequest request) {
        return service.add(request);
    }

    @PostMapping("/{id}/notify")
    public RestaurantWaitlistDto notifyGuest(@PathVariable Long id) {
        return service.notifyGuest(id);
    }

    @PostMapping("/{id}/seat")
    public RestaurantOrderDto seat(@PathVariable Long id, @RequestBody SeatReservationRequest request) {
        return service.seat(id, request);
    }

    @PostMapping("/{id}/leave")
    public RestaurantWaitlistDto leave(@PathVariable Long id) {
        return service.leave(id);
    }
}
