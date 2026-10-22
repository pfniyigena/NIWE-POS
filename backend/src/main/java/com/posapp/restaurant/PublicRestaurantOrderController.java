package com.posapp.restaurant;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.restaurant.dto.OnlineOrderingDtos.PublicMenu;
import com.posapp.restaurant.dto.OnlineOrderingDtos.PublicOrderRequest;
import com.posapp.restaurant.dto.OnlineOrderingDtos.Track;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The QR menu and the takeaway / delivery page: no login. A guest can read the menu, send an order that waits for staff, and follow it by its code.
 * Nothing here can touch an open bill; staff accept the request first.
 */
@RestController
@RequestMapping("/api/public/restaurant")
public class PublicRestaurantOrderController {

    private final RestaurantOnlineOrderService service;
    private final ModuleService moduleService;

    public PublicRestaurantOrderController(RestaurantOnlineOrderService service, ModuleService moduleService) {
        this.service = service;
        this.moduleService = moduleService;
    }

    @GetMapping("/menu")
    public PublicMenu menu(@RequestParam(required = false) String table) {
        moduleService.requireEnabled(ModuleKey.RESTAURANT);
        return service.menu(table);
    }

    @PostMapping("/orders")
    public Track place(@Valid @RequestBody PublicOrderRequest request) {
        moduleService.requireEnabled(ModuleKey.RESTAURANT);
        return service.place(request);
    }

    @GetMapping("/orders/{code}")
    public Track track(@PathVariable String code) {
        moduleService.requireEnabled(ModuleKey.RESTAURANT);
        return service.trackByCode(code);
    }

    @PostMapping("/orders/{code}/cancel")
    public Track cancel(@PathVariable String code) {
        moduleService.requireEnabled(ModuleKey.RESTAURANT);
        return service.cancelByGuest(code);
    }
}
