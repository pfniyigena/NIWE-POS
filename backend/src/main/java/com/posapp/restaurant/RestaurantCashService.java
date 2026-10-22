package com.posapp.restaurant;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.restaurant.dto.CashEventRequest;
import com.posapp.restaurant.dto.EndOfDayDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Writes the cash events the cash-up reads: deposit movements (automatically) and paid-in / paid-out entries (by hand). */
@Service
public class RestaurantCashService {

    private static final Set<String> MANUAL = Set.of("PAID_IN", "PAID_OUT", "TIPS_PAID");

    private final RestaurantCashEventRepository repository;
    private final RestaurantDayCloseRepository closeRepository;
    private final com.posapp.hotel.HotelClock clock;
    private final AuditLogService auditLogService;

    public RestaurantCashService(RestaurantCashEventRepository repository, RestaurantDayCloseRepository closeRepository,
                                 com.posapp.hotel.HotelClock clock, AuditLogService auditLogService) {
        this.repository = repository;
        this.closeRepository = closeRepository;
        this.clock = clock;
        this.auditLogService = auditLogService;
    }

    /** A deposit moved: kind is DEPOSIT_RECEIVED, DEPOSIT_REFUNDED or DEPOSIT_FORFEITED. Joins the caller's transaction. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deposit(String kind, Long reservationId, String label, String method, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        RestaurantCashEvent e = new RestaurantCashEvent();
        e.setKind(kind);
        e.setReservationId(reservationId);
        e.setLabel(label);
        e.setMethod(method);
        e.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        e.setByUser(RestaurantAccess.username());
        repository.save(e);
    }

    /** Cash put in or taken out of the drawer by hand. Any signed-in user may record it; it needs a reason and cannot be added to a closed day. */
    @Transactional
    public EndOfDayDto.CashEventDto addManual(CashEventRequest request) {
        String kind = request.getKind() == null ? "" : request.getKind().trim().toUpperCase();
        if (!MANUAL.contains(kind)) throw ApiException.badRequest("Choose paid in, paid out or tips paid out");
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(BigDecimal.valueOf(100_000_000)) > 0) throw ApiException.badRequest("The amount must be more than zero");
        String why = request.getReason() == null ? "" : request.getReason().trim();
        if (why.isEmpty()) throw ApiException.badRequest("Say what the money was for");
        if (why.length() > 200) throw ApiException.badRequest("The reason is too long (200 characters at most)");
        if (closeRepository.findByBusinessDateAndStatus(clock.today(), "CLOSED").isPresent()) {
            throw ApiException.badRequest("Today has already been closed; ask a manager to reopen it first");
        }
        RestaurantCashEvent e = new RestaurantCashEvent();
        e.setKind(kind);
        e.setAmount(amount);
        e.setReason(why);
        e.setLabel(label(kind));
        e.setByUser(RestaurantAccess.username());
        RestaurantCashEvent saved = repository.save(e);
        auditLogService.record("RESTAURANT_CASH_" + kind, label(kind) + " " + amount + ": " + why, "RestaurantCashEvent", saved.getId());
        return dto(saved);
    }

    /** A manager removes a hand-made entry (a typing mistake) while its day is still open. */
    @Transactional
    public void remove(Long id) {
        if (!RestaurantAccess.isApprover()) throw ApiException.forbidden("Only a manager can remove a cash entry");
        RestaurantCashEvent e = repository.findById(id).orElseThrow(() -> ApiException.notFound("Cash entry not found: " + id));
        if (!MANUAL.contains(e.getKind())) throw ApiException.badRequest("Deposit movements cannot be removed here");
        java.time.LocalDate day = e.getCreatedAt().atZone(clock.zone()).toLocalDate();
        if (closeRepository.findByBusinessDateAndStatus(day, "CLOSED").isPresent()) throw ApiException.badRequest("That day is closed; reopen it first");
        repository.delete(e);
        auditLogService.record("RESTAURANT_CASH_REMOVED", "Removed cash entry " + label(e.getKind()) + " " + e.getAmount() + " (" + e.getReason() + ")", "RestaurantCashEvent", id);
    }

    static String label(String kind) {
        return switch (kind) {
            case "PAID_IN" -> "Paid in";
            case "PAID_OUT" -> "Paid out";
            case "TIPS_PAID" -> "Tips paid out";
            case "DEPOSIT_RECEIVED" -> "Deposit received";
            case "DEPOSIT_REFUNDED" -> "Deposit refunded";
            case "DEPOSIT_FORFEITED" -> "Deposit kept";
            default -> kind;
        };
    }

    static EndOfDayDto.CashEventDto dto(RestaurantCashEvent e) {
        return new EndOfDayDto.CashEventDto(e.getId(), e.getCreatedAt(), e.getKind(), e.getMethod(), e.getAmount(), e.getLabel(), e.getReason(), e.getByUser());
    }
}
