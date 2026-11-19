package com.posapp.onlinestore;

import com.posapp.onlinestore.OnlineStoreDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Drivers, who takes an order, the delivery slip and the driver's run sheet (staff only). */
@RestController
public class OnlineDeliveryController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineDeliveryService delivery;
    private final OnlineOrderService orders;

    public OnlineDeliveryController(OnlineDeliveryService delivery, OnlineOrderService orders) {
        this.delivery = delivery;
        this.orders = orders;
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/drivers")
    public List<DriverDto> drivers() {
        return delivery.drivers();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/drivers")
    public DriverDto create(@Valid @RequestBody DriverRequest request) {
        return delivery.save(null, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/api/online-store/drivers/{id}")
    public DriverDto update(@PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return delivery.save(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/drivers/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        delivery.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/orders/{id}/driver")
    public OrderDto assign(@PathVariable Long id, @RequestBody AssignDriverRequest request) {
        return orders.assignDriver(id, request.driverId());
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/orders/{id}/delivery-slip")
    public ResponseEntity<byte[]> slip(@PathVariable Long id) {
        return pdf(delivery.slip(id), "delivery-slip-" + id + ".pdf");
    }

    /** driverId 0 = the delivery orders nobody has taken yet. */
    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/delivery-run")
    public ResponseEntity<byte[]> run(@RequestParam Long driverId) {
        return pdf(delivery.runSheet(driverId), "run-sheet-" + driverId + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"").body(bytes);
    }
}
