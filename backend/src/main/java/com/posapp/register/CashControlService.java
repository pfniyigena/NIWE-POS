package com.posapp.register;

import com.posapp.accounting.AccountService;
import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.common.ListPaging;
import com.posapp.order.OrderService;
import com.posapp.register.dto.CashMovementDto;
import com.posapp.register.dto.CashMovementRequest;
import com.posapp.register.dto.CashSummaryDto;
import com.posapp.register.dto.CloseSessionRequest;
import com.posapp.returns.ReturnsService;
import com.posapp.user.AccessArea;
import com.posapp.user.AppUser;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Cash that moves in and out of a till drawer outside a sale. Everything here changes what the drawer should hold (and so the expected
 * cash the count at closing is compared with), and the movements that really change the company's money are posted to Accounting.
 */
@Service
public class CashControlService {

    /** Reasons for money paid in: the label, and the account on the other side (null = just a transfer from the safe or bank, nothing to post). */
    /** Keeps the order given, so the reason lists on screen always start with the same, most usual choice. */
    private static Map<String, String> ordered(String... keyAndLabel) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyAndLabel.length; i += 2) m.put(keyAndLabel[i], keyAndLabel[i + 1]);
        return java.util.Collections.unmodifiableMap(m);
    }

    static final Map<String, String> IN_REASONS = ordered("FLOAT", "Change / float from the safe", "OWNER", "Owner's money put in");
    static final Map<String, String> OUT_REASONS = ordered("EXPENSE", "Small expense", "SUPPLIER", "Supplier paid from the till", "STAFF_ADVANCE", "Staff advance",
            "OWNER", "Owner's withdrawal", "OTHER", "Other");
    static final Map<String, String> DROP_REASONS = ordered("SAFE", "Dropped into the safe", "BANK", "Taken to the bank");

    private final CashMovementRepository repository;
    private final RegisterSessionRepository sessionRepository;
    private final CashierSessionRepository cashierSessionRepository;
    private final OrderService orderService;
    private final ReturnsService returnsService;
    private final PosSettingsService settingsService;
    private final ManagerApprovalService approvalService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final WarehouseService warehouseService;
    private final com.posapp.branch.BranchAccessService branchAccessService;

    public CashControlService(CashMovementRepository repository, RegisterSessionRepository sessionRepository, CashierSessionRepository cashierSessionRepository,
                              OrderService orderService, ReturnsService returnsService, PosSettingsService settingsService, ManagerApprovalService approvalService,
                              AccountingService accountingService, AuditLogService auditLogService, WarehouseService warehouseService,
                              com.posapp.branch.BranchAccessService branchAccessService) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.cashierSessionRepository = cashierSessionRepository;
        this.orderService = orderService;
        this.returnsService = returnsService;
        this.settingsService = settingsService;
        this.approvalService = approvalService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.warehouseService = warehouseService;
        this.branchAccessService = branchAccessService;
    }

    public record Totals(BigDecimal paidIn, BigDecimal paidOut, BigDecimal drops) {
    }

    @Transactional(readOnly = true)
    public Totals totals(Long sessionId) {
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO, drops = BigDecimal.ZERO;
        for (CashMovement m : repository.findByRegisterSessionIdOrderByCreatedAtAsc(sessionId)) {
            if (m.isVoided()) continue;
            switch (m.getKind()) {
                case "PAID_IN" -> in = in.add(m.getAmount());
                case "PAID_OUT" -> out = out.add(m.getAmount());
                case "DROP" -> drops = drops.add(m.getAmount());
                default -> {
                }
            }
        }
        return new Totals(in, out, drops);
    }

    /** What the drawer should hold now: float + cash sales - cash refunds + paid in - paid out - drops. */
    @Transactional(readOnly = true)
    public BigDecimal expectedCash(RegisterSession session) {
        BigDecimal sales = orderService.computeSessionTotals(session.getId()).cashIn();
        BigDecimal refunds = returnsService.sumCashRefundsForSession(session.getId());
        Totals t = totals(session.getId());
        return session.getOpeningFloat().add(sales).subtract(refunds).add(t.paidIn()).subtract(t.paidOut()).subtract(t.drops());
    }

    @Transactional(readOnly = true)
    public CashSummaryDto summary(Long sessionId) {
        RegisterSession session = session(sessionId);
        PosSettings cfg = settingsService.entity();
        BigDecimal sales = orderService.computeSessionTotals(sessionId).cashIn();
        BigDecimal refunds = returnsService.sumCashRefundsForSession(sessionId);
        Totals t = totals(sessionId);
        BigDecimal expected = session.getOpeningFloat().add(sales).subtract(refunds).add(t.paidIn()).subtract(t.paidOut()).subtract(t.drops());
        boolean alert = session.getStatus() == SessionStatus.OPEN && cfg.getDropAlertAmount().signum() > 0 && expected.compareTo(cfg.getDropAlertAmount()) > 0;
        return new CashSummaryDto(sessionId, session.getRegister() == null ? "" : session.getRegister().getName(), session.getStatus().name(), session.getOpeningFloat(),
                sales, refunds, t.paidIn(), t.paidOut(), t.drops(), expected, cfg.getPaidOutApprovalOver(), cfg.getDropAlertAmount(), alert,
                cfg.isDenominationCountRequired(), movements(sessionId));
    }

    @Transactional(readOnly = true)
    public List<CashMovementDto> movements(Long sessionId) {
        RegisterSession session = session(sessionId);
        String reg = session.getRegister() == null ? "" : session.getRegister().getName();
        return repository.findByRegisterSessionIdOrderByCreatedAtAsc(sessionId).stream().map(m -> dto(m, reg)).toList();
    }

    /** Cash movements across shifts for the manager's list: text (reason, note, who, register), kind, register, day. */
    @Transactional(readOnly = true)
    public List<CashMovementDto> filtered(String q, String kind, Long registerId, LocalDate from, LocalDate to) {
        Map<Long, RegisterSession> cache = new java.util.HashMap<>();
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> kind == null || kind.isBlank() || kind.equalsIgnoreCase("ALL") || m.getKind().equalsIgnoreCase(kind) || (kind.equalsIgnoreCase("VOIDED") && m.isVoided()))
                .filter(m -> ListPaging.between(m.getCreatedAt(), from, to))
                .filter(m -> {
                    RegisterSession s = cache.computeIfAbsent(m.getRegisterSessionId(), id -> sessionRepository.findById(id).orElse(null));
                    if (s == null) return false;
                    if (!branchAccessService.canAccess(warehouseService.branchIdOf(s.getRegister() != null ? s.getRegister().getWarehouse() : null))) return false;
                    if (registerId != null && (s.getRegister() == null || !registerId.equals(s.getRegister().getId()))) return false;
                    return ListPaging.has(q, s.getRegister() == null ? null : s.getRegister().getName(), m.getNote(), m.getByUser(), m.getApprovedBy(), reasonLabel(m));
                })
                .map(m -> {
                    RegisterSession s = cache.get(m.getRegisterSessionId());
                    return dto(m, s != null && s.getRegister() != null ? s.getRegister().getName() : "");
                }).toList();
    }

    @Transactional
    public CashMovementDto record(Long sessionId, CashMovementRequest req, AppUser caller) {
        RegisterSession session = session(sessionId);
        if (session.getStatus() != SessionStatus.OPEN) throw ApiException.badRequest("This shift is closed; cash can only be moved while it is open");
        CashierSession mine = requireOnShift(session, caller);
        String kind = req.getKind() == null ? "" : req.getKind().trim().toUpperCase();
        if (!List.of("PAID_IN", "PAID_OUT", "DROP", "COUNT").contains(kind)) throw ApiException.badRequest("Choose paid in, paid out, drop to safe or count");
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (note.length() > 200) throw ApiException.badRequest("The note is too long (200 characters at most)");
        PosSettings cfg = settingsService.entity();
        BigDecimal expected = expectedCash(session);

        CashMovement m = new CashMovement();
        m.setRegisterSessionId(sessionId);
        m.setCashierSessionId(mine == null ? null : mine.getId());
        m.setKind(kind);
        m.setNote(note.isEmpty() ? null : note);
        m.setByUser(caller.getUsername());
        m.setBranchId(branch(session));

        if (kind.equals("COUNT")) {
            BigDecimal counted = counted(req.getAmount(), req.getDenominations());
            m.setAmount(counted);
            m.setExpectedCash(expected);
            m.setVariance(counted.subtract(expected));
            m.setCountDetail(countJson(req.getDenominations()));
            CashMovement saved = repository.save(m);
            auditLogService.record("POS_CASH_COUNT", "Counted " + counted + " in the drawer of " + regName(session) + " (expected " + expected + ")", "CashMovement", saved.getId());
            return dto(saved, regName(session));
        }

        BigDecimal amount = req.getAmount() == null ? BigDecimal.ZERO : req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(BigDecimal.valueOf(1_000_000_000)) > 0) throw ApiException.badRequest("The amount must be more than zero");
        String reason = req.getReasonCode() == null ? "" : req.getReasonCode().trim().toUpperCase();
        Map<String, String> allowed = kind.equals("PAID_IN") ? IN_REASONS : kind.equals("PAID_OUT") ? OUT_REASONS : DROP_REASONS;
        if (!allowed.containsKey(reason)) throw ApiException.badRequest("Choose a reason: " + String.join(", ", allowed.keySet()));
        if ((reason.equals("OTHER") || reason.equals("EXPENSE") || reason.equals("SUPPLIER") || reason.equals("STAFF_ADVANCE")) && note.isEmpty()) {
            throw ApiException.badRequest("Say what the money was for");
        }
        if (!kind.equals("PAID_IN") && amount.compareTo(expected) > 0) {
            throw ApiException.badRequest("There should only be " + expected.setScale(2, RoundingMode.HALF_UP).toPlainString() + " in the drawer");
        }
        if (kind.equals("PAID_OUT") && cfg.getPaidOutApprovalOver().signum() > 0 && amount.compareTo(cfg.getPaidOutApprovalOver()) > 0) {
            if (ManagerApprovalService.canApprove(caller, ManagerApprovalService.Kind.CASH)) {
                m.setApprovedBy(caller.getUsername());
            } else {
                AppUser approver = approvalService.approverByPin(req.getApproverPin(), ManagerApprovalService.Kind.CASH);
                m.setApprovedBy(approver.getUsername());
            }
        }
        m.setReasonCode(reason);
        m.setAmount(amount);
        CashMovement saved = repository.save(m);
        postMovement(saved, session, false);
        auditLogService.record("POS_CASH_" + kind, label(kind) + " " + amount + " (" + allowed.get(reason) + ") on " + regName(session) + (note.isEmpty() ? "" : ": " + note), "CashMovement", saved.getId());
        return dto(saved, regName(session));
    }

    /** A manager cancels a mistaken movement while its shift is still open; the books are corrected the same way. */
    @Transactional
    public CashMovementDto voidMovement(Long id, String reason, AppUser caller) {
        if (!ManagerApprovalService.canApprove(caller, ManagerApprovalService.Kind.CASH)) throw ApiException.forbidden("Only a manager can void a cash movement");
        CashMovement m = repository.findById(id).orElseThrow(() -> ApiException.notFound("Cash movement not found: " + id));
        if (m.isVoided()) throw ApiException.badRequest("That entry is already voided");
        if (m.getKind().equals("COUNT")) throw ApiException.badRequest("A count cannot be voided");
        RegisterSession session = session(m.getRegisterSessionId());
        if (session.getStatus() != SessionStatus.OPEN) throw ApiException.badRequest("That shift is closed");
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw ApiException.badRequest("Say why it is being voided");
        if (why.length() > 200) throw ApiException.badRequest("The reason is too long (200 characters at most)");
        m.setVoided(true);
        m.setVoidReason(why);
        m.setVoidedBy(caller.getUsername());
        m.setVoidedAt(Instant.now());
        CashMovement saved = repository.save(m);
        postMovement(saved, session, true);
        auditLogService.record("POS_CASH_VOIDED", "Voided " + label(m.getKind()) + " " + m.getAmount() + " on " + regName(session) + ": " + why, "CashMovement", id);
        return dto(saved, regName(session));
    }

    private void postMovement(CashMovement m, RegisterSession session, boolean reverse) {
        String counter = null;
        boolean in = m.getKind().equals("PAID_IN");
        if (in && "OWNER".equals(m.getReasonCode())) counter = AccountService.OWNERS_EQUITY;
        if (m.getKind().equals("PAID_OUT")) {
            counter = switch (m.getReasonCode()) {
                case "SUPPLIER" -> AccountService.ACCOUNTS_PAYABLE;
                case "STAFF_ADVANCE" -> AccountService.STAFF_ADVANCES;
                case "OWNER" -> AccountService.OWNERS_EQUITY;
                default -> AccountService.TILL_EXPENSE;
            };
        }
        accountingService.postTillMovement(m.getId(), in, counter, m.getAmount(), "Till " + label(m.getKind()).toLowerCase() + " — " + reasonLabel(m) + " (" + regName(session) + ")", m.getBranchId(), reverse);
    }

    // ---- counting helpers shared with shift closing ----

    /** The cash counted: the total of the notes and coins when they were counted, else the typed total. */
    public static BigDecimal counted(BigDecimal typed, List<CloseSessionRequest.Denomination> denominations) {
        BigDecimal fromCount = null;
        if (denominations != null && !denominations.isEmpty()) {
            fromCount = BigDecimal.ZERO;
            for (CloseSessionRequest.Denomination d : denominations) {
                if (d.getValue() == null || d.getValue().signum() <= 0) throw ApiException.badRequest("Each note or coin needs a value above zero");
                int c = d.getCount() == null ? 0 : d.getCount();
                if (c < 0 || c > 1_000_000) throw ApiException.badRequest("The number of notes or coins must be between 0 and 1,000,000");
                fromCount = fromCount.add(d.getValue().multiply(BigDecimal.valueOf(c)));
            }
            fromCount = fromCount.setScale(2, RoundingMode.HALF_UP);
        }
        if (fromCount != null) {
            if (typed != null && typed.setScale(2, RoundingMode.HALF_UP).compareTo(fromCount) != 0) {
                throw ApiException.badRequest("The counted cash (" + typed.setScale(2, RoundingMode.HALF_UP).toPlainString() + ") does not match the notes and coins counted (" + fromCount.toPlainString() + ")");
            }
            return fromCount;
        }
        if (typed == null) throw ApiException.badRequest("Enter the cash counted in the drawer");
        if (typed.signum() < 0) throw ApiException.badRequest("Counted cash cannot be negative");
        return typed.setScale(2, RoundingMode.HALF_UP);
    }

    /** The notes and coins as a small JSON list, or null when none were counted. */
    public static String countJson(List<CloseSessionRequest.Denomination> denominations) {
        if (denominations == null || denominations.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (CloseSessionRequest.Denomination d : denominations) {
            if (d.getCount() == null || d.getCount() == 0) continue;
            if (!first) sb.append(',');
            sb.append("{\"value\":").append(d.getValue().stripTrailingZeros().toPlainString()).append(",\"count\":").append(d.getCount()).append('}');
            first = false;
        }
        return sb.append(']').toString();
    }

    // ---- small helpers ----

    private RegisterSession session(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Session not found: " + id));
    }

    /** A manager may move cash on any shift; anyone else must be working on it. */
    private CashierSession requireOnShift(RegisterSession session, AppUser caller) {
        CashierSession mine = cashierSessionRepository.findByRegisterSessionIdAndCashierIdAndStatus(session.getId(), caller.getId(), CashierSessionStatus.OPEN).orElse(null);
        if (mine == null && !ManagerApprovalService.isManager(caller) && !caller.canManage(AccessArea.SALES)) {
            throw ApiException.forbidden("Only someone working on this shift can move its cash");
        }
        return mine;
    }

    private Long branch(RegisterSession s) {
        return warehouseService.branchIdOf(s.getRegister() != null ? s.getRegister().getWarehouse() : null);
    }

    private static String regName(RegisterSession s) {
        return s.getRegister() == null ? "register" : s.getRegister().getName();
    }

    static String label(String kind) {
        return switch (kind) {
            case "PAID_IN" -> "Paid in";
            case "PAID_OUT" -> "Paid out";
            case "DROP" -> "Drop";
            case "COUNT" -> "Count";
            default -> kind;
        };
    }

    static String reasonLabel(CashMovement m) {
        if (m.getReasonCode() == null) return m.getKind().equals("COUNT") ? "Cash count" : "";
        Map<String, String> src = m.getKind().equals("PAID_IN") ? IN_REASONS : m.getKind().equals("PAID_OUT") ? OUT_REASONS : DROP_REASONS;
        return src.getOrDefault(m.getReasonCode(), m.getReasonCode());
    }

    private static CashMovementDto dto(CashMovement m, String register) {
        return CashMovementDto.from(m, register, reasonLabel(m));
    }
}
