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
    private final RestaurantCheckService checkService;

    public RestaurantOrderController(RestaurantOrderService orderService, RestaurantCheckService checkService) {
        this.checkService = checkService;
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
    public org.springframework.http.ResponseEntity<List<RestaurantOrderDto>> listClosed(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String type,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String method,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long serverId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size) {
        boolean filtered = (q != null && !q.isBlank()) || (status != null && !status.isBlank()) || (type != null && !type.isBlank())
                || (method != null && !method.isBlank()) || serverId != null || from != null || to != null;
        return com.posapp.common.ListPaging.of(orderService.closedFiltered(q, status, type, method, serverId, from, to, size == null && !filtered), page, size);
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

    /** Hotel module: a room-service order for a checked-in guest. */
    @PostMapping("/orders/room-service")
    public RestaurantOrderDto openRoomServiceOrder(@Valid @RequestBody com.posapp.restaurant.dto.OpenRoomServiceOrderRequest request) {
        return orderService.openRoomServiceOrder(request);
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
    public RestaurantOrderDto removeLine(@PathVariable Long id, @PathVariable Long lineId, @org.springframework.web.bind.annotation.RequestParam(required = false) String reason) {
        return orderService.removeLine(id, lineId, reason);
    }

    /** Gives one item free (manager only), with a reason. */
    @PostMapping("/orders/{id}/lines/{lineId}/comp")
    public RestaurantOrderDto comp(@PathVariable Long id, @PathVariable Long lineId, @RequestBody java.util.Map<String, String> body) {
        return orderService.compLine(id, lineId, body == null ? null : body.get("reason"));
    }

    @DeleteMapping("/orders/{id}/lines/{lineId}/comp")
    public RestaurantOrderDto uncomp(@PathVariable Long id, @PathVariable Long lineId) {
        return orderService.uncompLine(id, lineId);
    }

    /** Splits the chosen items off into a new check at the same table; returns the new check. */
    @PostMapping("/orders/{id}/split")
    public RestaurantOrderDto split(@PathVariable Long id, @RequestBody com.posapp.restaurant.dto.CheckLineSelection body) {
        return checkService.split(id, body);
    }

    /** Moves the chosen items onto another open bill; returns the receiving bill. */
    @PostMapping("/orders/{id}/transfer")
    public RestaurantOrderDto transfer(@PathVariable Long id, @RequestBody com.posapp.restaurant.dto.CheckLineSelection body) {
        return checkService.transfer(id, body);
    }

    @PostMapping("/orders/{id}/move-table")
    public RestaurantOrderDto moveTable(@PathVariable Long id, @RequestBody java.util.Map<String, Long> body) {
        return checkService.moveTable(id, body == null ? null : body.get("tableId"));
    }

    /** Records that the receipt was printed (from the second print it is marked as a copy and logged). */
    @PostMapping("/orders/{id}/print")
    public RestaurantOrderDto print(@PathVariable Long id) {
        return checkService.recordPrint(id);
    }

    /** Sets or removes the order discount (a percentage or an amount, with a reason). Above the restaurant's limit a manager must do it. */
    @org.springframework.web.bind.annotation.PutMapping("/orders/{id}/discount")
    public RestaurantOrderDto setDiscount(@PathVariable Long id, @RequestBody com.posapp.restaurant.dto.OrderDiscountRequest request) {
        return orderService.setOrderDiscount(id, request);
    }

    /** Waives or restores the service charge on one order (waiving needs a manager). */
    @org.springframework.web.bind.annotation.PutMapping("/orders/{id}/service-charge")
    public RestaurantOrderDto setServiceCharge(@PathVariable Long id, @RequestBody com.posapp.restaurant.dto.ServiceChargeRequest request) {
        return orderService.setServiceChargeWaived(id, request.isWaived());
    }

    @PostMapping("/orders/{id}/send-to-kitchen")
    public RestaurantOrderDto sendToKitchen(@PathVariable Long id) {
        return orderService.sendToKitchen(id);
    }

    /** Releases the waiting lines of a course (and earlier ones) to the kitchen. */
    @PostMapping("/orders/{id}/fire-course")
    public RestaurantOrderDto fireCourse(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> body) {
        Integer course = body == null ? null : body.get("course");
        if (course == null) throw com.posapp.common.ApiException.badRequest("Say which course to fire");
        return orderService.fireCourse(id, course);
    }

    /** Sets the allergens the guests at this order cannot have. */
    @org.springframework.web.bind.annotation.PutMapping("/orders/{id}/guest-allergens")
    public RestaurantOrderDto setGuestAllergens(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return orderService.setGuestAllergens(id, body == null ? null : body.get("allergens"));
    }

    @PostMapping("/orders/{id}/checkout")
    public RestaurantOrderDto checkout(@PathVariable Long id, @Valid @RequestBody RestaurantCheckoutRequest request) {
        return orderService.checkout(id, request);
    }

    @PostMapping("/orders/{id}/cancel")
    public RestaurantOrderDto cancel(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        return orderService.cancel(id, body == null ? null : body.get("reason"));
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
