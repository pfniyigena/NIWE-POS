package com.posapp.restaurant;

import com.posapp.restaurant.dto.RestaurantWastageDto;
import com.posapp.restaurant.dto.RestaurantWastageRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fourier: "Restaurants need a dedicated wastage module." Listing/logging is open to any
 * logged-in staff (no class-level {@code @PreAuthorize}), same tier as {@code
 * RestaurantOrderController} and the rest of the module's day-to-day screens (Orders/Floor
 * plan/Kitchen display/...) — logging a dropped plate or a burnt batch is kitchen/front-of-house
 * work, not back-office admin, unlike the general Inventory > Stock Adjustments screen (which now
 * mirrors this same PENDING/CONFIRMED/REJECTED shape — see StockAdjustmentController). Per
 * Fourier's later "should be confirmed by admin once confirmed inventory and account will be
 * impacted", {@link #confirm}/{@link #reject} below are gated to ADMIN — plus, per Fourier's "Build
 * a full permissions system", anyone additionally granted {@code Permission.CONFIRM_RESTAURANT_WASTAGE}.
 */
@RestController
@RequestMapping("/api/restaurant/wastage")
public class RestaurantWastageController {

    private final RestaurantWastageService wastageService;

    public RestaurantWastageController(RestaurantWastageService wastageService) {
        this.wastageService = wastageService;
    }

    @GetMapping
    public List<RestaurantWastageDto> listAll() {
        return wastageService.listAll();
    }

    @PostMapping
    public RestaurantWastageDto create(@Valid @RequestBody RestaurantWastageRequest request) {
        return wastageService.create(request);
    }

    /** ADMIN or PERM_CONFIRM_RESTAURANT_WASTAGE — moves a PENDING entry to CONFIRMED, which is what actually decrements stock and posts to Accounting. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CONFIRM_RESTAURANT_WASTAGE')")
    @PostMapping("/{id}/confirm")
    public RestaurantWastageDto confirm(@PathVariable Long id) {
        return wastageService.confirm(id);
    }

    /** ADMIN or PERM_CONFIRM_RESTAURANT_WASTAGE — discards a PENDING entry (e.g. logged in error) with no stock/Accounting impact. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CONFIRM_RESTAURANT_WASTAGE')")
    @PostMapping("/{id}/reject")
    public RestaurantWastageDto reject(@PathVariable Long id) {
        return wastageService.reject(id);
    }
}
