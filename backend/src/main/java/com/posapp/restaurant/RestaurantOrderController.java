package com.posapp.restaurant;

import com.posapp.restaurant.dto.AddRestaurantLineRequest;
import com.posapp.restaurant.dto.AssignDriverRequest;
import com.posapp.restaurant.dto.DeliveryStatusRequest;
import com.posapp.restaurant.dto.KitchenStatusRequest;
import com.posapp.restaurant.dto.KitchenTicketDto;
import com.posapp.restaurant.dto.MergeOrdersRequest;
import com.posapp.restaurant.dto.OpenOffPremiseOrderRequest;
import com.posapp.restaurant.dto.OpenTableOrderRequest;
import com.posapp.restaurant.dto.RestaurantCheckoutRequest;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import com.posapp.restaurant.dto.StaffOptionDto;
import com.posapp.restaurant.dto.UpdateRestaurantLineRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin/staff > Restaurant > Tables & Kitchen — the dine-in order-taking flow, open to any logged-in user (same tier as the POS terminal's own OrderController). */
@RestController
@RequestMapping("/api/restaurant")
public class RestaurantOrderController {

    private final RestaurantOrderService orderService;

    public RestaurantOrderController(RestaurantOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders/open")
    public List<RestaurantOrderDto> listOpen() {
        return orderService.listOpen();
    }

    @GetMapping("/orders/{id}")
    public RestaurantOrderDto getById(@PathVariable Long id) {
        return orderService.getById(id);
    }

    /** Restaurant Receipts screen (lookup + reprint) — open to any logged-in user, same as the POS terminal's own Receipts screen. */
    @GetMapping("/orders/closed")
    public List<RestaurantOrderDto> listClosed() {
        return orderService.listClosed();
    }

    @PostMapping("/tables/{tableId}/orders")
    public RestaurantOrderDto openOrder(@PathVariable Long tableId, @RequestBody(required = false) OpenTableOrderRequest request) {
        return orderService.openOrder(tableId, request != null ? request : new OpenTableOrderRequest());
    }

    // ---- Delivery & takeaway (Fourier: "Delivery & takeaway") ----

    @PostMapping("/orders/takeaway")
    public RestaurantOrderDto openTakeawayOrder(@Valid @RequestBody OpenOffPremiseOrderRequest request) {
        return orderService.openTakeawayOrder(request);
    }

    @PostMapping("/orders/delivery")
    public RestaurantOrderDto openDeliveryOrder(@Valid @RequestBody OpenOffPremiseOrderRequest request) {
        return orderService.openDeliveryOrder(request);
    }

    @GetMapping("/orders/deliveries")
    public List<RestaurantOrderDto> listDeliveries() {
        return orderService.listDeliveries();
    }

    @PostMapping("/orders/{id}/assign-driver")
    public RestaurantOrderDto assignDriver(@PathVariable Long id, @Valid @RequestBody AssignDriverRequest request) {
        return orderService.assignDriver(id, request);
    }

    @PostMapping("/orders/{id}/delivery-status")
    public RestaurantOrderDto setDeliveryStatus(@PathVariable Long id, @Valid @RequestBody DeliveryStatusRequest request) {
        return orderService.setDeliveryStatus(id, request.getStatus());
    }

    /** Driver-picker options — see StaffOptionDto's javadoc for why this is its own open endpoint rather than the ADMIN-only /api/users. */
    @GetMapping("/staff")
    public List<StaffOptionDto> listActiveStaff() {
        return orderService.listActiveStaff();
    }

    @PostMapping("/orders/{id}/lines")
    public RestaurantOrderDto addLine(@PathVariable Long id, @Valid @RequestBody AddRestaurantLineRequest request) {
        return orderService.addLine(id, request);
    }

    @PatchMapping("/orders/{id}/lines/{lineId}")
    public RestaurantOrderDto updateLine(@PathVariable Long id, @PathVariable Long lineId, @RequestBody UpdateRestaurantLineRequest request) {
        return orderService.updateLine(id, lineId, request);
    }

    @DeleteMapping("/orders/{id}/lines/{lineId}")
    public RestaurantOrderDto removeLine(@PathVariable Long id, @PathVariable Long lineId) {
        return orderService.removeLine(id, lineId);
    }

    @PostMapping("/orders/{id}/send-to-kitchen")
    public RestaurantOrderDto sendToKitchen(@PathVariable Long id) {
        return orderService.sendToKitchen(id);
    }

    @PostMapping("/orders/{id}/checkout")
    public RestaurantOrderDto checkout(@PathVariable Long id, @Valid @RequestBody RestaurantCheckoutRequest request) {
        return orderService.checkout(id, request);
    }

    @PostMapping("/orders/{id}/cancel")
    public RestaurantOrderDto cancel(@PathVariable Long id) {
        return orderService.cancel(id);
    }

    @PostMapping("/orders/{id}/merge")
    public RestaurantOrderDto merge(@PathVariable Long id, @Valid @RequestBody MergeOrdersRequest request) {
        return orderService.mergeInto(id, request);
    }

    // ---- Kitchen Display (Fourier: "Kitchen Order Tickets / Kitchen Display") ----

    @GetMapping("/kitchen/tickets")
    public List<KitchenTicketDto> listKitchenTickets() {
        return orderService.listKitchenTickets();
    }

    @PostMapping("/orders/{id}/lines/{lineId}/kitchen-status")
    public RestaurantOrderDto setLineKitchenStatus(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody KitchenStatusRequest request) {
        return orderService.setLineKitchenStatus(id, lineId, request.getStatus());
    }
}
