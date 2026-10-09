package com.posapp.register;

import com.posapp.register.dto.AssignCashierRequest;
import com.posapp.register.dto.CashierSessionDto;
import com.posapp.register.dto.PinRequest;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Joining/closing/listing cashier sessions within one shift.
 *
 * join/mine/close stay open to any logged-in user — any cashier standing at the terminal needs to
 * be able to identify themselves (join/mine) and end their own working session (close). Seeing
 * *every* cashier session on a shift (list) is ADMIN-only (Change 5: a cashier can access their
 * own session; only an admin can see them all), and force-closing every session on a shift at
 * once (close-all) is likewise ADMIN-only (Change 2: a user closes their own session, but an
 * admin can close all of them).
 */
@RestController
@RequestMapping("/api/sessions/{registerSessionId}/cashier-sessions")
public class CashierSessionController {

    private final CashierSessionService cashierSessionService;

    public CashierSessionController(CashierSessionService cashierSessionService) {
        this.cashierSessionService = cashierSessionService;
    }

    /** Someone *other* than the browser's logged-in user steps in, identifying themselves by PIN alone. */
    @PostMapping
    public CashierSessionDto join(@PathVariable Long registerSessionId, @Valid @RequestBody PinRequest request) {
        return cashierSessionService.joinShift(registerSessionId, request);
    }

    /** No PIN needed — resolves (or silently creates) the caller's own cashier session on this shift, from their JWT. */
    @GetMapping("/mine")
    public CashierSessionDto mine(@PathVariable Long registerSessionId, @AuthenticationPrincipal AppUserPrincipal principal) {
        return cashierSessionService.getOrStartMine(registerSessionId, principal.getUser());
    }

    /**
     * Admin-only: adds a specific staff member's cashier session to this shift directly, no PIN
     * required — the admin-side equivalent of "Switch cashier" at the terminal, for when the
     * admin doesn't have that cashier's PIN in hand (or isn't at the physical terminal at all).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign")
    public CashierSessionDto assign(@PathVariable Long registerSessionId, @Valid @RequestBody AssignCashierRequest request) {
        return cashierSessionService.assignCashier(registerSessionId, request.getCashierId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<CashierSessionDto> list(@PathVariable Long registerSessionId) {
        return cashierSessionService.listForShift(registerSessionId);
    }

    /** The cashier ends their own session; an admin may also close someone else's this way (see CashierSessionService.close). */
    @PostMapping("/{cashierSessionId}/close")
    public CashierSessionDto close(@PathVariable Long registerSessionId,
                                    @PathVariable Long cashierSessionId,
                                    @AuthenticationPrincipal AppUserPrincipal principal) {
        return cashierSessionService.close(registerSessionId, cashierSessionId, principal.getUser());
    }

    /** Admin bulk action: end every open cashier session on this shift without closing the shift itself. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/close-all")
    public List<CashierSessionDto> closeAll(@PathVariable Long registerSessionId) {
        return cashierSessionService.closeAllForShift(registerSessionId);
    }
}
