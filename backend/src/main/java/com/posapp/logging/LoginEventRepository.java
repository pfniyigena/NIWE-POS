package com.posapp.logging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {

    /** outcome is "", "OK" or "FAILED"; unset bounds arrive as sentinel instants (see AuditLogRepository for why). */
    @Query("select e from LoginEvent e where " +
           "(:q = '' or lower(e.username) like lower(concat('%', :q, '%')) or lower(e.ip) like lower(concat('%', :q, '%'))) " +
           "and (:outcome = '' or (:outcome = 'OK' and e.success = true) or (:outcome = 'FAILED' and e.success = false)) " +
           "and e.createdAt >= :from and e.createdAt <= :to order by e.createdAt desc")
    Page<LoginEvent> search(String q, String outcome, Instant from, Instant to, Pageable pageable);

    long countBySuccessAndCreatedAtGreaterThanEqual(boolean success, Instant since);

    @Query("select count(distinct e.ip) from LoginEvent e where e.success = false and e.createdAt >= :since")
    long countFailingAddressesSince(Instant since);
}
