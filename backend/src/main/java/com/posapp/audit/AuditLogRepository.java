package com.posapp.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * The admin viewer's own filtered/paged query — mirrors {@code ProductRepository.searchAllForAdmin}'s
     * null-safe parameter style (an unset filter is passed as {@code null}/{@code ""} rather than
     * omitted from the query, since JPQL can't conditionally include a clause at all). {@code q}
     * matches against both {@code actorUsername}/{@code actorFullName} and {@code description} —
     * an admin searching the log usually doesn't know or care which of "who" or "what" they're
     * matching on.
     * <p>
     * {@code from}/{@code to} are always non-null by the time they get here — {@code
     * AuditLogService.search} normalizes an unset bound to a sentinel {@code Instant} (epoch /
     * far-future) rather than passing {@code null}. That's deliberate: a bare {@code :from is
     * null} check as the *only* context for that parameter gives the PostgreSQL JDBC driver
     * nothing to infer its type from, and the whole prepared statement fails to plan with
     * "could not determine data type of parameter $N" — the same class of bug already worked
     * around for the String {@code q} parameter above (there, by comparing against {@code ''}
     * instead of checking {@code is null}). So this query is written as a plain range check with
     * no {@code is null} branch at all, and the sentinel normalization does the "unset" work
     * instead.
     */
    @Query("select a from AuditLog a where " +
           "(:q = '' or lower(a.actorUsername) like lower(concat('%', :q, '%')) " +
           "or lower(a.actorFullName) like lower(concat('%', :q, '%')) " +
           "or lower(a.description) like lower(concat('%', :q, '%'))) " +
           "and (:action = '' or a.action = :action) " +
           "and a.createdAt >= :from and a.createdAt <= :to " +
           "order by a.createdAt desc")
    Page<AuditLog> search(String q, String action, Instant from, Instant to, Pageable pageable);

    /** Powers the admin viewer's action-type filter dropdown — every distinct code actually in use, not a hardcoded enum list that could drift from what's really been recorded. */
    @Query("select distinct a.action from AuditLog a order by a.action asc")
    List<String> findDistinctActions();
}
