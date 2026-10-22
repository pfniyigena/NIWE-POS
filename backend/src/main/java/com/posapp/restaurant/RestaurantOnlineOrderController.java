package com.posapp.restaurant;

import com.posapp.restaurant.dto.OnlineOrderingDtos.AcceptRequest;
import com.posapp.restaurant.dto.OnlineOrderingDtos.OnlineOrderDto;
import com.posapp.restaurant.dto.OnlineOrderingDtos.RejectRequest;
import com.posapp.restaurant.dto.OnlineOrderingDtos.TableQr;
import com.posapp.restaurant.dto.OnlineOrderingDtos.ZoneDto;
import com.posapp.restaurant.dto.OnlineOrderingDtos.ZoneRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Restaurant > Online orders: the queue of requests from the QR menu and the ordering page, plus delivery areas and table QR codes. */
@RestController
@RequestMapping("/api/restaurant")
public class RestaurantOnlineOrderController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final RestaurantOnlineOrderService service;

    public RestaurantOnlineOrderController(RestaurantOnlineOrderService service) {
        this.service = service;
    }

    @GetMapping("/online-orders")
    public ResponseEntity<List<OnlineOrderDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String mode,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(service.list(q, status, mode, from, to), page, size);
    }

    @GetMapping("/online-orders/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("pending", service.pendingCount());
    }

    @GetMapping("/online-orders/{id}")
    public OnlineOrderDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/online-orders/{id}/accept")
    public OnlineOrderDto accept(@PathVariable Long id, @RequestBody(required = false) AcceptRequest request) {
        return service.accept(id, request);
    }

    @PostMapping("/online-orders/{id}/reject")
    public OnlineOrderDto reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        return service.reject(id, request.getReason());
    }

    @PostMapping("/online-orders/expire-stale")
    @PreAuthorize(MANAGE)
    public Map<String, Integer> expireStale() {
        return Map.of("expired", service.expireStale());
    }

    // ---- delivery areas

    @GetMapping("/delivery-zones")
    public List<ZoneDto> zones() {
        return service.zones();
    }

    @PostMapping("/delivery-zones")
    @PreAuthorize(MANAGE)
    public ZoneDto createZone(@RequestBody ZoneRequest request) {
        return service.saveZone(null, request);
    }

    @PutMapping("/delivery-zones/{id}")
    @PreAuthorize(MANAGE)
    public ZoneDto updateZone(@PathVariable Long id, @RequestBody ZoneRequest request) {
        return service.saveZone(id, request);
    }

    @DeleteMapping("/delivery-zones/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
        service.deleteZone(id);
        return ResponseEntity.noContent().build();
    }

    // ---- table QR codes

    @GetMapping("/qr")
    @PreAuthorize(MANAGE)
    public List<TableQr> qr() {
        return service.qrList();
    }

    @PostMapping("/qr/{tableId}/regenerate")
    @PreAuthorize(MANAGE)
    public TableQr regenerate(@PathVariable Long tableId) {
        return service.regenerateQr(tableId);
    }
}
