package com.posapp.register;

import com.posapp.register.dto.CashierSessionDto;
import com.posapp.register.dto.CashierSessionZReportDto;
import com.posapp.register.dto.SetActiveOrderRequest;
import com.posapp.security.AppUserPrincipal;
import com.posapp.user.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Backs the plain "Sessions" screen — a cross-shift list of individual cashier sessions (not one
 * row per shift; see CashierSession's javadoc). Open to any logged-in user (Change: "Cashier does
 * not have access to his session data"), but what comes back is scoped by role, not just by what
 * the frontend happens to ask for: an ADMIN sees every cashier session that has ever existed,
 * across every shift, so they can close any of them without first drilling into which shift it
 * belongs to (Change: "Admin can close each cashier_session"); anyone else sees only their own
 * (Change: "Cashier can see only his session").
 *
 * <p>As of the customer-display feature (Task: "Add customer-facing double screen"), this
 * controller also carries a single-session read ({@code GET /{id}}) and the terminal's write of
 * its "what's on screen" pointer ({@code PUT /{id}/active-order}) — both flat, not nested under a
 * {@code registerSessionId} the way {@link CashierSessionController}'s writes are, since a display
 * device only ever needs the one cashier session id to poll, not the shift it belongs to. Both are
 * ownership-or-admin gated in the service layer (CashierSessionService.requireOwnSessionOrAdmin),
 * the same rule {@code close} already uses.</p>
 */
@RestController
@RequestMapping("/api/cashier-sessions")
public class CashierSessionQueryController {

    private final CashierSessionService cashierSessionService;

    public CashierSessionQueryController(CashierSessionService cashierSessionService) {
        this.cashierSessionService = cashierSessionService;
    }

    @GetMapping
    public List<CashierSessionDto> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        var caller = principal.getUser();
        return caller.canManage(com.posapp.user.AccessArea.SALES)
                ? cashierSessionService.listAll()
                : cashierSessionService.listMine(caller);
    }

    /** Polled by the customer-facing display to learn which order is currently on screen. */
    @GetMapping("/{id}")
    public CashierSessionDto getOne(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return cashierSessionService.getById(id, principal.getUser());
    }

    /** Called by the terminal every time the cashier switches tabs — never by the display itself. */
    @PutMapping("/{id}/active-order")
    public CashierSessionDto setActiveOrder(@PathVariable Long id, @RequestBody SetActiveOrderRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return cashierSessionService.setActiveOrder(id, request.getOrderId(), principal.getUser());
    }

    /**
     * This one cashier session's own printable report — Task: "We need to differentiate
     * CashierSession Z Report with RegisterSession Z Report." Deliberately a separate read from
     * {@code GET /api/sessions/{id}/report} (the whole shift's Z-report): this is just this
     * cashier's own start/end time and sales, never the shift's cash-drawer reconciliation or other
     * cashiers' figures. Same ownership-or-admin gate as {@link #getOne}.
     */
    @GetMapping("/{id}/report")
    public CashierSessionZReportDto report(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return cashierSessionService.report(id, principal.getUser());
    }
}
