package com.posapp.restaurant;

import com.posapp.restaurant.dto.CashEventRequest;
import com.posapp.restaurant.dto.EndOfDayDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Paid in / paid out / tips paid out entries for the drawer. Any signed-in user may record one (like the till); only a manager can remove one. */
@RestController
@RequestMapping("/api/restaurant/cash-events")
public class RestaurantCashController {

    private final RestaurantCashService service;

    public RestaurantCashController(RestaurantCashService service) {
        this.service = service;
    }

    @PostMapping
    public EndOfDayDto.CashEventDto add(@RequestBody CashEventRequest request) {
        return service.addManual(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }
}
