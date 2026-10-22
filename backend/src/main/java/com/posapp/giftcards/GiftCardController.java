package com.posapp.giftcards;

import com.posapp.common.ApiException;
import com.posapp.giftcards.GiftCardDtos.*;
import com.posapp.order.OrderService;
import com.posapp.register.CashierSession;
import com.posapp.security.AppUserPrincipal;
import com.posapp.user.AccessArea;
import com.posapp.user.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code /api/giftcards}. The till can look a card up and sell or top one up on its own open cashier session; the back office (list, history, block, expiry, releasing expired cards, giving
 * a card away free) needs the Sales area.
 */
@RestController
@RequestMapping("/api/giftcards")
public class GiftCardController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')";

    private final GiftCardService service;
    private final OrderService orderService;

    public GiftCardController(GiftCardService service, OrderService orderService) {
        this.service = service;
        this.orderService = orderService;
    }

    @PreAuthorize(VIEW)
    @GetMapping
    public ResponseEntity<List<Row>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String filter,
                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return com.posapp.common.ListPaging.of(service.list(q, filter), page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/summary")
    public Summary summary() {
        return service.summary();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/{id}")
    public Detail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    /** The till: type or scan a code, see what is left on the card. */
    @GetMapping("/lookup")
    public Row lookup(@RequestParam String code) {
        return service.lookup(code);
    }

    /**
     * Sells a card. At the till the request carries the cashier session the money goes into; without one it is a back-office sale and needs Sales management. Only a Sales manager
     * may give a card away free.
     */
    @PostMapping
    public Row sell(@RequestBody SellRequest r, @AuthenticationPrincipal AppUserPrincipal principal) {
        AppUser caller = principal.getUser();
        boolean manager = caller.canManage(AccessArea.SALES);
        CashierSession cs = sessionFor(r == null ? null : r.cashierSessionId(), caller, manager);
        return service.sell(r, cs, manager);
    }

    @PostMapping("/{id}/topup")
    public Row topUp(@PathVariable Long id, @RequestBody TopUpRequest r, @AuthenticationPrincipal AppUserPrincipal principal) {
        AppUser caller = principal.getUser();
        boolean manager = caller.canManage(AccessArea.SALES);
        CashierSession cs = sessionFor(r == null ? null : r.cashierSessionId(), caller, manager);
        return service.topUp(id, r, cs, manager);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/block")
    public Row block(@PathVariable Long id) {
        return service.setBlocked(id, true);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/unblock")
    public Row unblock(@PathVariable Long id) {
        return service.setBlocked(id, false);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/expiry")
    public Row expiry(@PathVariable Long id, @RequestBody ExpiryRequest r) {
        return service.setExpiry(id, r == null ? null : r.expiresOn());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/release-expired")
    public Released releaseExpired() {
        return service.releaseExpired();
    }

    private CashierSession sessionFor(Long cashierSessionId, AppUser caller, boolean manager) {
        if (cashierSessionId != null) return orderService.sellingSession(cashierSessionId, caller);
        if (!manager) throw ApiException.forbidden("Sell gift cards from an open till.");
        return null;
    }
}
