package com.posapp.register;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.order.OrderService;
import com.posapp.order.SessionTotals;
import com.posapp.register.dto.*;
import com.posapp.returns.ReturnsService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import com.posapp.warehouse.LocationType;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RegisterService {

    private final RegisterRepository registerRepository;
    private final RegisterSessionRepository sessionRepository;
    private final CashierSessionService cashierSessionService;
    private final OrderService orderService;
    private final ReturnsService returnsService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    /** Fourier: "the terminal will be registered on warehouse" — resolves RegisterRequest.warehouseId; see Register.warehouse's own javadoc. */
    private final WarehouseService warehouseService;
    /** Staff limited to certain branches only see their branches' registers and shifts. */
    private final com.posapp.branch.BranchAccessService branchAccessService;

    public RegisterService(RegisterRepository registerRepository,
                            RegisterSessionRepository sessionRepository,
                            CashierSessionService cashierSessionService,
                            OrderService orderService,
                            ReturnsService returnsService,
                            UserRepository userRepository,
                            AuditLogService auditLogService,
                            WarehouseService warehouseService,
                            com.posapp.branch.BranchAccessService branchAccessService) {
        this.registerRepository = registerRepository;
        this.sessionRepository = sessionRepository;
        this.cashierSessionService = cashierSessionService;
        this.orderService = orderService;
        this.returnsService = returnsService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.warehouseService = warehouseService;
        this.branchAccessService = branchAccessService;
    }

    /** Null warehouseId means "no warehouse assigned" — see Register.warehouse's own javadoc for what that means at checkout. */
    private Warehouse resolveWarehouse(Long warehouseId) {
        return warehouseId != null ? warehouseService.getEntityOrThrow(warehouseId) : null;
    }

    /**
     * Null defaultLocationId means "no location override" — see Register.defaultLocation's own
     * javadoc. When set, it must belong to {@code warehouse} (the one just resolved from the same
     * request) — a location picked from a different warehouse than the one this register is
     * actually assigned to would silently mean something other than what the admin screen showed,
     * so this is rejected outright rather than guessed at. Fourier: "location type of
     * RECEIVING,SALES,STORAGE,DAMAGED" / "Type drives behavior directly" — a register can only ever
     * be pinned to a SALES-type location (see {@code LocationType}'s own javadoc); the frontend's
     * own location picker already filters to SALES only, but this is re-checked here too since the
     * backend is the one that actually has to enforce it.
     */
    private WarehouseLocation resolveDefaultLocation(Long defaultLocationId, Warehouse warehouse) {
        if (defaultLocationId == null) {
            return null;
        }
        WarehouseLocation location = warehouseService.getLocationOrThrow(defaultLocationId);
        if (warehouse == null || location.getWarehouse() == null || !location.getWarehouse().getId().equals(warehouse.getId())) {
            throw ApiException.badRequest("'" + location.getName() + "' does not belong to the selected warehouse");
        }
        if (location.getType() != LocationType.SALES) {
            throw ApiException.badRequest("'" + location.getName() + "' is a " + location.getType() + " location — a register can only be assigned a SALES location");
        }
        return location;
    }

    @Transactional(readOnly = true)
    public List<RegisterDto> listRegisters() {
        return registerRepository.findAll().stream()
                .filter(r -> branchAccessService.canAccess(warehouseService.branchIdOf(r.getWarehouse())))
                .map(r -> RegisterDto.from(r, sessionRepository.findByRegisterIdAndStatus(r.getId(), SessionStatus.OPEN)
                        .map(RegisterSession::getId).orElse(null)))
                .toList();
    }

    /** Task: "Implement an audit log of who did what and when" — register/terminal changes are a curated action, see AuditLogService's javadoc. */
    @Transactional
    public RegisterDto createRegister(RegisterRequest request) {
        Register register = new Register();
        register.setName(request.getName());
        register.setLocation(request.getLocation());
        Warehouse warehouse = resolveWarehouse(request.getWarehouseId());
        register.setWarehouse(warehouse);
        register.setDefaultLocation(resolveDefaultLocation(request.getDefaultLocationId(), warehouse));
        Register saved = registerRepository.save(register);
        auditLogService.record("REGISTER_CREATED", "Created terminal " + saved.getName(), "Register", saved.getId());
        return RegisterDto.from(saved, null);
    }

    /** Task: "Implement an audit log of who did what and when" — see createRegister's identical treatment just above. */
    @Transactional
    public RegisterDto updateRegister(Long id, RegisterRequest request) {
        Register register = registerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Register not found: " + id));
        register.setName(request.getName());
        register.setLocation(request.getLocation());
        Warehouse warehouse = resolveWarehouse(request.getWarehouseId());
        register.setWarehouse(warehouse);
        register.setDefaultLocation(resolveDefaultLocation(request.getDefaultLocationId(), warehouse));
        Register saved = registerRepository.save(register);
        auditLogService.record("REGISTER_UPDATED", "Updated terminal " + saved.getName(), "Register", saved.getId());
        Long openSessionId = sessionRepository.findByRegisterIdAndStatus(id, SessionStatus.OPEN)
                .map(RegisterSession::getId).orElse(null);
        return RegisterDto.from(saved, openSessionId);
    }

    /**
     * Opening a shift is ADMIN-only (Change 3), but the admin isn't necessarily the person who's
     * actually going to ring sales on it — so the admin picks who is (Change: "when open session
     * you need to select the user to be assigned this session"), and that staff member, not the
     * calling admin, becomes the shift's assigned cashier and gets its first cashier session.
     */
    @Transactional
    public SessionDto openSession(Long registerId, OpenSessionRequest request, AppUser caller) {
        Register register = registerRepository.findById(registerId)
                .orElseThrow(() -> ApiException.notFound("Register not found: " + registerId));
        sessionRepository.findByRegisterIdAndStatus(registerId, SessionStatus.OPEN).ifPresent(existing -> {
            throw ApiException.conflict("Register '" + register.getName() + "' already has an open session");
        });

        AppUser assignedCashier = userRepository.findById(request.getCashierId())
                .orElseThrow(() -> ApiException.notFound("Staff member not found: " + request.getCashierId()));
        if (!assignedCashier.isActive()) {
            throw ApiException.badRequest("'" + assignedCashier.getFullName() + "' is not an active staff account");
        }

        RegisterSession session = new RegisterSession();
        session.setRegister(register);
        session.setCashier(assignedCashier);
        session.setOpeningFloat(request.getOpeningFloat());
        session.setStatus(SessionStatus.OPEN);
        RegisterSession saved = sessionRepository.save(session);

        // The assigned cashier gets their own cashier session right away — not the admin who
        // opened the shift. Anyone else who wants to work this same shift joins afterward with
        // their PIN — see CashierSessionController.
        CashierSessionDto assignedSession = cashierSessionService.getOrStartMine(saved.getId(), assignedCashier);
        orderService.startNewOrder(assignedSession.id(), caller);
        return toDto(saved);
    }

    /** Used by the plain "Sessions" list (any user) and the admin "Shifts" screen — same data, different audience/filtering on the frontend. */
    @Transactional(readOnly = true)
    public List<SessionDto> listSessions() {
        return sessionRepository.findAllByOrderByOpenedAtDesc().stream()
                .filter(s -> branchAccessService.canAccess(warehouseService.branchIdOf(s.getRegister() != null ? s.getRegister().getWarehouse() : null)))
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SessionDto getSession(Long sessionId) {
        return toDto(getSessionOrThrow(sessionId));
    }

    @Transactional
    public SessionDto closeSession(Long sessionId, CloseSessionRequest request) {
        RegisterSession session = getSessionOrThrow(sessionId);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw ApiException.badRequest("Session is already closed: " + sessionId);
        }

        SessionTotals totals = orderService.computeSessionTotals(sessionId);
        BigDecimal cashRefundsOut = returnsService.sumCashRefundsForSession(sessionId);
        BigDecimal expectedCash = session.getOpeningFloat().add(totals.cashIn()).subtract(cashRefundsOut);

        session.setCountedCash(request.getCountedCash());
        session.setCashVariance(request.getCountedCash().subtract(expectedCash));
        session.setStatus(SessionStatus.CLOSED);
        session.setClosedAt(java.time.Instant.now());

        RegisterSession saved = sessionRepository.save(session);
        cashierSessionService.closeAllForShift(sessionId);
        return SessionDto.from(saved, totals);
    }

    @Transactional(readOnly = true)
    public SessionReportDto sessionReport(Long sessionId) {
        RegisterSession session = getSessionOrThrow(sessionId);
        SessionTotals totals = orderService.computeSessionTotals(sessionId);
        BigDecimal cashRefundsOut = returnsService.sumCashRefundsForSession(sessionId);
        BigDecimal expectedCash = session.getOpeningFloat().add(totals.cashIn()).subtract(cashRefundsOut);

        // Task: "The report for register_session (Shift) should show all cashier_session (session
        // report)" — one line per cashier who worked this shift (almost always just the one the
        // shift was opened for; more when "Switch cashier"/admin "assign" brought others on), each
        // with its own start/end time and its own slice of the shift's sales.
        List<CashierSessionReportDto> cashierSessions = cashierSessionService.listEntitiesForShift(sessionId).stream()
                .map(cs -> CashierSessionReportDto.from(cs, orderService.computeCashierSessionTotals(cs.getId())))
                .toList();

        return new SessionReportDto(
                SessionDto.from(session, totals),
                totals.saleCount(),
                totals.grossSales(),
                totals.taxCollected(),
                totals.cashIn(),
                totals.nonCashIn(),
                cashRefundsOut,
                expectedCash,
                cashierSessions
        );
    }

    RegisterSession getSessionOrThrow(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found: " + sessionId));
    }

    private SessionDto toDto(RegisterSession session) {
        return SessionDto.from(session, orderService.computeSessionTotals(session.getId()));
    }
}
