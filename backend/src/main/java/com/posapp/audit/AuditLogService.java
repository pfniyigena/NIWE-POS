package com.posapp.audit;

import com.posapp.audit.dto.AuditLogDto;
import com.posapp.common.PageResponse;
import com.posapp.security.AppUserPrincipal;
import com.posapp.user.AppUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Task: "Implement an audit log of who did what and when." Records one {@link AuditLog} row per
 * meaningful business action across the app — a curated set of "key business actions"
 * (Fourier's own choice when asked to scope this): settings changes (business details, system
 * settings, inventory settings, tax rates, payment methods, EFRIS Rwanda/Chad terminal settings),
 * user management (create/update/reset password/set PIN/self password change), product
 * create/update/deactivate, register create/update, app install/uninstall/enable/disable, the two
 * "undo" actions — voiding a sale and processing a return — and, since the "Inventory depth"
 * follow-up ({@code com.posapp.inventory}), supplier create/update/delete, purchase order
 * create/update/send/cancel/receive, and stock adjustments/write-offs. Deliberately NOT every
 * mutating request (a generic request-logging filter/aspect was one option Fourier was offered
 * and didn't pick): a cashier adding a line to a sale, or updating a cart, isn't the kind of thing
 * an audit trail needs to read as noise — the log should stay a short list of things worth
 * explaining to someone asking "who changed this and when," not a mirror of the HTTP access log.
 * <p>
 * {@link #record} pulls the acting user off {@link SecurityContextHolder} itself rather than
 * requiring every caller to pass one in — every one of the dozen-plus services this is called
 * from already runs inside an authenticated request by the time it calls this (the same
 * {@code SecurityContextHolder} {@code JwtAuthFilter} populates on every request), so this avoids
 * threading an extra {@code AppUser}/{@code AppUserPrincipal} parameter through methods whose
 * signatures are otherwise stable and used elsewhere. Never throws: a log entry is a side effect
 * of the real action, not a precondition for it, so a missing/malformed authentication just
 * records {@code "system"} as the actor rather than failing the caller's own transaction over it.
 * <p>
 * Joins the caller's own transaction (default propagation) rather than {@code REQUIRES_NEW} —
 * every call site below records only once the real change has actually been made (after the
 * repository save, before the method returns), so if something later in that same transaction
 * still rolls it back, the audit entry rolling back with it is correct: nothing worth auditing
 * actually happened.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    /**
     * Sentinel bounds used in place of a {@code null} {@code from}/{@code to} in {@link #search}.
     * See that method's comment for why: binding a raw {@code null} {@code Instant} into a query
     * whose only use of that parameter is an {@code is null} check gives the PostgreSQL JDBC
     * driver no type context to infer from, and fails the whole query with "could not determine
     * data type of parameter $N." Using an always-non-null, effectively-unbounded value instead
     * sidesteps that entirely — {@code AuditLog} rows are never dated before this app existed or
     * after the year 9999, so these bounds are functionally equivalent to "no filter."
     */
    private static final Instant EARLIEST_BOUND = Instant.EPOCH;
    private static final Instant LATEST_BOUND = Instant.parse("9999-12-31T23:59:59Z");

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** For an action with no single affected entity (e.g. enabling/disabling an app). */
    @Transactional
    public void record(String action, String description) {
        record(action, description, null, null);
    }

    @Transactional
    public void record(String action, String description, String entityType, Long entityId) {
        AppUser actor = currentActor();
        AuditLog log = new AuditLog();
        log.setActorId(actor != null ? actor.getId() : null);
        log.setActorUsername(actor != null ? actor.getUsername() : "system");
        log.setActorFullName(actor != null ? actor.getFullName() : null);
        log.setAction(action);
        log.setDescription(description);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        repository.save(log);
    }

    /**
     * The admin viewer's own paged/filtered listing — see {@code AuditLogRepository.search}'s
     * javadoc for what each filter matches. {@code q}/{@code action} are normalized to {@code ""}
     * (never {@code null}) for the same JDBC-type-inference reason {@code CatalogService
     * .searchProducts} normalizes its own {@code q}. {@code from}/{@code to} get the same
     * treatment but with sentinel {@code Instant}s instead of {@code ""} — an unset bound (the
     * caller passes {@code null} when the admin didn't set a date filter) becomes {@link
     * #EARLIEST_BOUND}/{@link #LATEST_BOUND} rather than reaching the repository as {@code null},
     * which is what was crashing this endpoint with "could not determine data type of parameter
     * $N" (Postgres can't infer a type for a null Instant parameter whose only use is a bare
     * {@code is null} check).
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(String q, String action, Instant from, Instant to, int page, int size) {
        String normalizedQ = (q == null || q.isBlank()) ? "" : q.trim();
        String normalizedAction = (action == null || action.isBlank()) ? "" : action.trim();
        Instant normalizedFrom = (from == null) ? EARLIEST_BOUND : from;
        Instant normalizedTo = (to == null) ? LATEST_BOUND : to;
        int clampedSize = Math.max(1, Math.min(size, 200));
        int clampedPage = Math.max(0, page);
        // Unsorted Pageable, same convention as ProductRepository.search — the @Query above
        // already carries its own fixed "order by ... desc", so no Sort is layered on top here.
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        return PageResponse.from(repository.search(normalizedQ, normalizedAction, normalizedFrom, normalizedTo, pageable).map(AuditLogDto::from));
    }

    /** Every distinct action code ever recorded — powers the viewer's filter dropdown. */
    @Transactional(readOnly = true)
    public List<String> listDistinctActions() {
        return repository.findDistinctActions();
    }

    private AppUser currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return principal.getUser();
    }
}
