package com.posapp.promotions;

import com.posapp.order.OrderRepository;
import com.posapp.promotions.PromotionDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** {@code /api/promotions}: seeing needs the Sales area; changing needs Sales management (admin always). */
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')";

    private final PromotionService service;
    private final OrderRepository orders;

    public PromotionController(PromotionService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @PreAuthorize(VIEW)
    @GetMapping
    public List<Row> list() {
        return service.list();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/{id}")
    public Row get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/{id}/uses")
    public List<UseRow> uses(@PathVariable Long id) {
        return service.usesOf(id, orders);
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public Row create(@RequestBody Request r) {
        return service.create(r);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public Row update(@PathVariable Long id, @RequestBody Request r) {
        return service.update(id, r);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
