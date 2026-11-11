package com.posapp.onlinestore;

import com.posapp.common.ListPaging;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Online Store for staff: the order queue, the shop settings, the products on the shop and the delivery areas. */
@RestController
@RequestMapping("/api/online-store")
public class OnlineStoreController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineOrderService orders;
    private final OnlineStoreAdminService admin;

    public OnlineStoreController(OnlineOrderService orders, OnlineStoreAdminService admin) {
        this.orders = orders;
        this.admin = admin;
    }

    // ---------------------------------------------------------------- orders

    @PreAuthorize(VIEW)
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(orders.list(q, status), page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/orders/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("pending", orders.pendingCount());
    }

    @PreAuthorize(VIEW)
    @GetMapping("/orders/{id}")
    public OrderDto get(@PathVariable Long id) {
        return orders.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/accept")
    public OrderDto accept(@PathVariable Long id, @RequestBody(required = false) AcceptRequest request) {
        return orders.accept(id, request != null && request.overrideShortage());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/reject")
    public OrderDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return orders.reject(id, request.reason());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/pack")
    public OrderDto pack(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.pack(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/out-for-delivery")
    public OrderDto outForDelivery(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.outForDelivery(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/ready-for-pickup")
    public OrderDto readyForPickup(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.readyForPickup(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/complete")
    public OrderDto complete(@PathVariable Long id, @RequestBody(required = false) CompleteRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return orders.complete(id, request, principal.getUser());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.cancel(id, request == null ? null : request.note());
    }

    // ---------------------------------------------------------------- settings

    @PreAuthorize(VIEW)
    @GetMapping("/settings")
    public SettingsDto settings() {
        return admin.settings();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsDto request) {
        return admin.update(request);
    }

    // ---------------------------------------------------------------- products on the shop

    @PreAuthorize(VIEW)
    @GetMapping("/listings")
    public List<ListingDto> listings() {
        return admin.listings();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/listings")
    public ListingDto saveListing(@Valid @RequestBody ListingRequest request) {
        return admin.saveListing(request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/listings/{id}")
    public ResponseEntity<Void> removeListing(@PathVariable Long id) {
        admin.removeListing(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- delivery areas

    @PreAuthorize(VIEW)
    @GetMapping("/zones")
    public List<ZoneDto> zones() {
        return admin.zones();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/zones")
    public ZoneDto createZone(@Valid @RequestBody ZoneRequest request) {
        return admin.createZone(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/zones/{id}")
    public ZoneDto updateZone(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        return admin.updateZone(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/zones/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
        admin.deleteZone(id);
        return ResponseEntity.noContent().build();
    }
}
