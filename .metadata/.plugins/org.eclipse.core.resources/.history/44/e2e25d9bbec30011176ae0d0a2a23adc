package com.posapp.register;

import com.posapp.common.ApiException;
import com.posapp.order.OrderService;
import com.posapp.order.SessionTotals;
import com.posapp.register.dto.CashierSessionDto;
import com.posapp.register.dto.CashierSessionZReportDto;
import com.posapp.register.dto.PinRequest;
import com.posapp.user.AppUser;
import com.posapp.user.Role;
import com.posapp.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Lets more than one cashier work the same shift (RegisterSession/till) at once. Each cashier
 * "joins" the shift by typing their own PIN; this never touches or widens the JWT-based
 * authorization that actually guards the API — it only tags which cashier a tab/sale belongs to.
 * See {@link CashierSession}'s class javadoc for the full reasoning.
 */
@Service
public class CashierSessionService {

    private final CashierSessionRepository cashierSessionRepository;
    private final RegisterSessionRepository registerSessionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderService orderService;
    /** Staff limited to certain branches can only work a till at their branches — see {@link #assertBranchAccess}. */
    private final com.posapp.branch.BranchAccessService branchAccessService;
    private final com.posapp.warehouse.WarehouseService warehouseService;

    public CashierSessionService(CashierSessionRepository cashierSessionRepository,
                                  RegisterSessionRepository registerSessionRepository,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  OrderService orderService,
                                  com.posapp.branch.BranchAccessService branchAccessService,
                                  com.posapp.warehouse.WarehouseService warehouseService) {
        this.cashierSessionRepository = cashierSessionRepository;
        this.registerSessionRepository = registerSessionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.orderService = orderService;
        this.branchAccessService = branchAccessService;
        this.warehouseService = warehouseService;
    }

    /**
     * A cashier limited to certain branches (see AppUser.branchIds) can only work a shift whose
     * register belongs to one of them; admins and unrestricted users always can.
     */
    private void assertBranchAccess(RegisterSession shift, AppUser cashier) {
        Long branchId = warehouseService.branchIdOf(shift.getRegister() != null ? shift.getRegister().getWarehouse() : null);
        if (!branchAccessService.canAccess(cashier, branchId)) {
            throw ApiException.forbidden("'" + cashier.getFullName() + "' isn't assigned to the branch this till belongs to");
        }
    }

    /**
     * A cashier identifies themselves at the terminal with just their PIN — no username needed
     * (PINs are enforced unique among active staff in UserService.setPin). This is for a
     * *different* cashier than whoever the browser is logged in as (JWT) to step up to the same
     * physical terminal — see {@link #getOrStartMine} for the no-PIN case. If they already have
     * an open cashier session on this shift (e.g. they stepped away and are coming back), that
     * same session is resumed rather than creating a second one.
     */
    @Transactional
    public CashierSessionDto joinShift(Long registerSessionId, PinRequest request) {
        RegisterSession shift = getOpenShiftOrThrow(registerSessionId);
        AppUser cashier = findByPinOrThrow(request.getPin());
        assertBranchAccess(shift, cashier);
        return findOrCreate(shift, cashier);
    }

    /**
     * The no-PIN path: resolves (creating if needed) the *caller's own* cashier session on this
     * shift, identified by their JWT — used both to auto-create the opener's session the moment
     * they open the till, and to silently resume it whenever the terminal is loaded/reloaded
     * under that same login. PIN entry (joinShift) is only for someone *else* to step in.
     */
    @Transactional
    public CashierSessionDto getOrStartMine(Long registerSessionId, AppUser cashier) {
        RegisterSession shift = getOpenShiftOrThrow(registerSessionId);
        assertBranchAccess(shift, cashier);
        return findOrCreate(shift, cashier);
    }

    /**
     * Admin adds another staff member's cashier session directly onto an already-open shift — no
     * PIN needed. Without this, the only way to add a second cashier to a shift was "Switch
     * cashier" at the physical terminal, which needs that cashier's PIN in hand; and since
     * Registers &amp; Terminal is now ADMIN-only, an admin going to "Go to terminal" themselves
     * auto-creates *their own* cashier session there (see CashierSessionController#mine), leaving
     * no way to get the actually-intended cashier a session at all. This mirrors
     * RegisterService.openSession's "assign to" staff picker, just for a shift that's already open.
     */
    @Transactional
    public CashierSessionDto assignCashier(Long registerSessionId, Long cashierId) {
        RegisterSession shift = getOpenShiftOrThrow(registerSessionId);
        AppUser cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> ApiException.notFound("Staff member not found: " + cashierId));
        if (!cashier.isActive()) {
            throw ApiException.badRequest("'" + cashier.getFullName() + "' is not an active staff account");
        }
        assertBranchAccess(shift, cashier);
        return findOrCreate(shift, cashier);
    }

    private CashierSessionDto findOrCreate(RegisterSession shift, AppUser cashier) {
        CashierSession session = cashierSessionRepository
                .findByRegisterSessionIdAndCashierIdAndStatus(shift.getId(), cashier.getId(), CashierSessionStatus.OPEN)
                .orElseGet(() -> cashierSessionRepository.save(newSession(shift, cashier)));
        return CashierSessionDto.from(session);
    }

    private RegisterSession getOpenShiftOrThrow(Long registerSessionId) {
        RegisterSession shift = registerSessionRepository.findById(registerSessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found: " + registerSessionId));
        if (shift.getStatus() != SessionStatus.OPEN) {
            throw ApiException.badRequest("This till session is closed: " + registerSessionId);
        }
        return shift;
    }

    /** Used by the admin Shifts screen to show which cashiers worked a given shift. */
    @Transactional(readOnly = true)
    public List<CashierSessionDto> listForShift(Long registerSessionId) {
        return cashierSessionRepository.findByRegisterSessionIdOrderByStartedAtAsc(registerSessionId)
                .stream().map(CashierSessionDto::from).toList();
    }

    /**
     * Same lookup as {@link #listForShift}, but the entities rather than the DTO — used by
     * RegisterService.sessionReport (Task: "The report for register_session (Shift) should show
     * all cashier_session (session report)") which needs each {@link CashierSession}'s id to also
     * pull its own sales totals, not just its display fields.
     */
    @Transactional(readOnly = true)
    public List<CashierSession> listEntitiesForShift(Long registerSessionId) {
        return cashierSessionRepository.findByRegisterSessionIdOrderByStartedAtAsc(registerSessionId);
    }

    /**
     * Every cashier session that has ever existed, across every shift — the ADMIN branch of the
     * plain "Sessions" screen (see CashierSessionQueryController), which is meant to be the one
     * place an admin can see and close *any* individual cashier session without first drilling
     * into which shift it belongs to.
     */
    @Transactional(readOnly = true)
    public List<CashierSessionDto> listAll() {
        return cashierSessionRepository.findAllByOrderByStartedAtDesc()
                .stream().map(CashierSessionDto::from).toList();
    }

    /**
     * A cashier's own session history across every shift they've ever worked — the non-admin
     * branch of the same "Sessions" screen (Change: "Cashier does not have access to his session
     * data"). A cashier only ever sees and can close their own sessions from this screen.
     */
    @Transactional(readOnly = true)
    public List<CashierSessionDto> listMine(AppUser cashier) {
        return cashierSessionRepository.findAllByCashierIdOrderByStartedAtDesc(cashier.getId())
                .stream().map(CashierSessionDto::from).toList();
    }

    /**
     * A cashier ends their own working session (Change: "User closes his/her session"). Anyone
     * can call this for a session that's theirs; only an ADMIN can close someone *else's* — see
     * closeAllForShift for the admin bulk equivalent. Any cart the cashier never checked out is
     * discarded rather than left orphaned (see OrderService.discardAllOpenForCashierSession).
     */
    @Transactional
    public CashierSessionDto close(Long registerSessionId, Long cashierSessionId, AppUser caller) {
        CashierSession session = cashierSessionRepository.findById(cashierSessionId)
                .orElseThrow(() -> ApiException.notFound("Cashier session not found: " + cashierSessionId));
        if (!session.getRegisterSession().getId().equals(registerSessionId)) {
            throw ApiException.badRequest("Cashier session does not belong to this shift: " + cashierSessionId);
        }
        requireOwnSessionOrAdmin(session, caller, "You can only close your own session");
        if (session.getStatus() == CashierSessionStatus.OPEN) {
            orderService.discardAllOpenForCashierSession(session.getId());
            session.setStatus(CashierSessionStatus.CLOSED);
            session.setEndedAt(Instant.now());
            cashierSessionRepository.save(session);
        }
        return CashierSessionDto.from(session);
    }

    /**
     * Closes every still-open cashier session on a shift at once — used both by
     * RegisterService.closeSession (closing the shift implicitly ends every session on it) and by
     * the admin-only bulk "close all sessions" action (Change: "Admin can close all session"),
     * which can be used without closing the shift itself.
     */
    @Transactional
    public List<CashierSessionDto> closeAllForShift(Long registerSessionId) {
        List<CashierSession> open = cashierSessionRepository.findByRegisterSessionIdAndStatus(registerSessionId, CashierSessionStatus.OPEN);
        Instant now = Instant.now();
        for (CashierSession session : open) {
            orderService.discardAllOpenForCashierSession(session.getId());
            session.setStatus(CashierSessionStatus.CLOSED);
            session.setEndedAt(now);
        }
        return cashierSessionRepository.saveAll(open).stream().map(CashierSessionDto::from).toList();
    }

    /**
     * A single cashier session by id — backs the customer-facing display's polling (Task: "Add
     * customer-facing double screen") and the ownership check itself. Same "own session or admin"
     * rule as {@link #close}: a display device needs to be logged in as either the cashier this
     * session belongs to, or an admin — there's no separate, unauthenticated "public" read path,
     * consistent with every other endpoint in this app requiring a login.
     */
    @Transactional(readOnly = true)
    public CashierSessionDto getById(Long cashierSessionId, AppUser caller) {
        CashierSession session = getSessionOrThrow(cashierSessionId);
        requireOwnSessionOrAdmin(session, caller, "You can only view your own session");
        return CashierSessionDto.from(session);
    }

    /**
     * Sets (or clears) which tab the terminal currently has on screen, for a customer-facing
     * display polling {@link #getById} to follow (Task: "Add customer-facing double screen").
     * Called by the terminal itself every time the cashier switches tabs — never by the display,
     * which only ever reads this. Same ownership rule as every other cashier-session-scoped write.
     */
    @Transactional
    public CashierSessionDto setActiveOrder(Long cashierSessionId, Long orderId, AppUser caller) {
        CashierSession session = getSessionOrThrow(cashierSessionId);
        requireOwnSessionOrAdmin(session, caller, "You can only update your own session's display");
        session.setActiveOrderId(orderId);
        return CashierSessionDto.from(cashierSessionRepository.save(session));
    }

    /**
     * A standalone, printable report scoped to exactly this one cashier session — Task: "We need
     * to differentiate CashierSession Z Report with RegisterSession Z Report." Same
     * own-session-or-admin(-or-shares-this-shift) gate as {@link #getById}/{@link #close}; the
     * sales figures are this cashier's own slice of the shift only (see
     * {@link com.posapp.order.OrderService#computeCashierSessionTotals}), never the whole till's.
     */
    @Transactional(readOnly = true)
    public CashierSessionZReportDto report(Long cashierSessionId, AppUser caller) {
        CashierSession session = getSessionOrThrow(cashierSessionId);
        requireOwnSessionOrAdmin(session, caller, "You can only view your own session's report");
        SessionTotals totals = orderService.computeCashierSessionTotals(cashierSessionId);
        return CashierSessionZReportDto.from(session, totals);
    }

    private CashierSession getSessionOrThrow(Long cashierSessionId) {
        return cashierSessionRepository.findById(cashierSessionId)
                .orElseThrow(() -> ApiException.notFound("Cashier session not found: " + cashierSessionId));
    }

    /**
     * Shared by close/getById/setActiveOrder — a cashier may act on their own session, an ADMIN
     * may act on anyone's, and (same "Switch cashier" carve-out as {@code
     * OrderService.requireOwnCashierSessionOrAdmin}, see its javadoc for the full bug writeup) a
     * caller who themselves has their own open cashier session on this same shift may act on a
     * teammate's session on that shift — otherwise "End my session"/the customer display broke
     * the instant a cashier switched to someone else's PIN on a non-admin-owned browser login.
     */
    private void requireOwnSessionOrAdmin(CashierSession session, AppUser caller, String message) {
        boolean isOwnSession = session.getCashier() != null && session.getCashier().getId().equals(caller.getId());
        if (isOwnSession || caller.getRole() == Role.ADMIN) {
            return;
        }
        boolean sharesThisShift = session.getRegisterSession() != null
                && cashierSessionRepository.findByRegisterSessionIdAndCashierIdAndStatus(
                        session.getRegisterSession().getId(), caller.getId(), CashierSessionStatus.OPEN)
                        .isPresent();
        if (!sharesThisShift) {
            throw ApiException.forbidden(message);
        }
    }

    private CashierSession newSession(RegisterSession shift, AppUser cashier) {
        CashierSession session = new CashierSession();
        session.setRegisterSession(shift);
        session.setCashier(cashier);
        session.setStatus(CashierSessionStatus.OPEN);
        return session;
    }

    /**
     * PINs are stored hashed (like passwords), so there's no indexed lookup — this checks the
     * entered PIN against every active, PIN-enabled user's hash. Fine for a staff roster of the
     * size a single POS terminal deals with; UserService.setPin is what keeps this unambiguous by
     * refusing to let two active users share a PIN.
     */
    private AppUser findByPinOrThrow(String pin) {
        return userRepository.findByActiveTrueAndPinHashIsNotNull().stream()
                .filter(user -> passwordEncoder.matches(pin, user.getPinHash()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("PIN not recognized"));
    }
}
