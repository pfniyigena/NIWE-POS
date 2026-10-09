package com.posapp.order;

import com.posapp.email.EmailDocumentRequest;
import com.posapp.order.dto.*;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * The terminal's tab bar — scoped to one cashier's own working session within a shift, not
     * the whole shift, so cashiers sharing a till (see CashierSession) each see only their own
     * open carts. Enforced server-side, not just by what the frontend happens to ask for — see
     * OrderService.requireOwnCashierSessionOrAdmin.
     */
    @GetMapping("/cashier-sessions/{cashierSessionId}/orders")
    public List<OrderDto> listOpenOrders(@PathVariable Long cashierSessionId,
                                          @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.listOpenOrders(cashierSessionId, principal.getUser());
    }

    @PostMapping("/cashier-sessions/{cashierSessionId}/orders")
    public OrderDto startNewOrder(@PathVariable Long cashierSessionId,
                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.startNewOrder(cashierSessionId, principal.getUser());
    }

    /**
     * A cashier's own order history on one cashier session — every status, not just the open tabs
     * that {@link #listOpenOrders} returns. Reached from the "Sessions" screen's per-session
     * "Orders" view (Change: "he need to see orders on his session"). Same ownership-or-admin
     * enforcement as {@code listOpenOrders} — see OrderService.requireOwnCashierSessionOrAdmin.
     */
    @GetMapping("/cashier-sessions/{cashierSessionId}/orders/all")
    public List<OrderDto> listAllOrders(@PathVariable Long cashierSessionId,
                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.listAllForCashierSession(cashierSessionId, principal.getUser());
    }

    @GetMapping("/orders/{id}")
    public OrderDto getOrder(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.getOrder(id, principal.getUser());
    }

    @GetMapping("/orders/by-invoice/{invoiceNumber}")
    public OrderDto getByInvoiceNumber(@PathVariable String invoiceNumber) {
        return orderService.findByInvoiceNumber(invoiceNumber);
    }

    /** Fourier: "sending other documents like ... receipts ... to the customer's email" — see OrderService.emailReceipt's javadoc. */
    @PostMapping("/orders/{id}/email-receipt")
    public void emailReceipt(@PathVariable Long id, @RequestBody(required = false) EmailDocumentRequest request) {
        orderService.emailReceipt(id, request != null ? request.getEmail() : null);
    }

    /**
     * Recent completed sales for the "Receipts" screen (lookup + reprint) — open to any logged-in
     * user. Pass `sessionId` to scope the list to one till session (used when Receipts is opened
     * from inside the terminal); omit it for the app-wide standalone Receipts page.
     */
    @GetMapping("/orders/completed")
    public List<OrderDto> listRecentCompletedOrders(@RequestParam(required = false) Long sessionId) {
        return orderService.listRecentCompletedOrders(sessionId);
    }

    @PostMapping("/orders/{id}/lines")
    public OrderDto addLine(@PathVariable Long id, @Valid @RequestBody AddLineRequest request,
                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.addLine(id, request, principal.getUser());
    }

    @PostMapping("/orders/{id}/lines/by-barcode")
    public OrderDto addLineByBarcode(@PathVariable Long id, @Valid @RequestBody AddByBarcodeRequest request,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.addLineByBarcode(id, request, principal.getUser());
    }

    @PatchMapping("/orders/{id}/lines/{lineId}")
    public OrderDto updateLine(@PathVariable Long id, @PathVariable Long lineId, @RequestBody UpdateLineRequest request,
                                @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.updateLine(id, lineId, request, principal.getUser());
    }

    @DeleteMapping("/orders/{id}/lines/{lineId}")
    public OrderDto removeLine(@PathVariable Long id, @PathVariable Long lineId,
                                @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.removeLine(id, lineId, principal.getUser());
    }

    @PostMapping("/orders/{id}/customer")
    public OrderDto setCustomer(@PathVariable Long id, @RequestBody SetCustomerRequest request,
                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.setCustomer(id, request, principal.getUser());
    }

    @PostMapping("/orders/{id}/park")
    public OrderDto park(@PathVariable Long id, @RequestBody ParkRequest request,
                          @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.park(id, request, principal.getUser());
    }

    @PostMapping("/orders/{id}/discard")
    public OrderDto discardOrder(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return orderService.discardOrder(id, principal.getUser());
    }

    @PostMapping("/orders/{id}/checkout")
    public OrderDto checkout(@PathVariable Long id, @Valid @RequestBody CheckoutRequest request,
                              @AuthenticationPrincipal AppUserPrincipal principal) {
        OrderDto completed = orderService.checkout(id, request, principal.getUser());
        return orderService.submitToEfrisIfApplicable(completed.id());
    }

    /**
     * Fourier's own request: "if the sale order did not pass through with EFRIS (when there is an
     * error) add a button to retry if the error is fixed" — the receipt screen's "Retry EFRIS
     * submission" button (shown next to the existing failure warning) calls this. Just re-runs
     * {@link OrderService#submitToEfrisIfApplicable}, the exact same method the original checkout
     * called — that method's own guards (not COMPLETED, already has an {@code sdcId}, EFRIS not
     * enabled, no connection configured) already make a repeat call safe, so there's nothing
     * retry-specific to add here beyond exposing it as its own endpoint. A retry on a sale that
     * isn't actually failed (already fiscalized, or never attempted) is simply a no-op — the same
     * as it always was when checkout's own call happened to run twice.
     */
    @PostMapping("/orders/{id}/efris/retry")
    public OrderDto retryEfris(@PathVariable Long id) {
        return orderService.submitToEfrisIfApplicable(id);
    }

    /**
     * Task: "local queuing of sales that sync once connectivity returns" — replays one whole sale
     * that was rung offline. See OrderService.syncOfflineSale's javadoc for the idempotency and
     * ownership rules (deliberately looser than the rest of this controller: the cashier session
     * doesn't need to still be open).
     */
    @PostMapping("/orders/offline-sync")
    public OrderDto syncOfflineSale(@Valid @RequestBody OfflineSaleRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        OrderDto synced = orderService.syncOfflineSale(request, principal.getUser());
        return orderService.submitToEfrisIfApplicable(synced.id());
    }
}
