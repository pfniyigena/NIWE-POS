package com.posapp.restaurant;

import com.posapp.restaurant.dto.RestaurantSettingsDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restaurant pricing rules. Anyone signed in can read them (the till needs the tip choices); changing them needs Operations manage rights. */
@RestController
@RequestMapping("/api/restaurant/settings")
public class RestaurantSettingsController {

    private final RestaurantSettingsService service;

    public RestaurantSettingsController(RestaurantSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public RestaurantSettingsDto get() {
        return service.get();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping
    public RestaurantSettingsDto update(@RequestBody RestaurantSettingsDto request) {
        return service.update(request);
    }
}
