package com.posapp.register;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashierSessionRepository extends JpaRepository<CashierSession, Long> {

    /** Used by joinShift to resume an already-open session instead of creating a duplicate for the same cashier. */
    Optional<CashierSession> findByRegisterSessionIdAndCashierIdAndStatus(Long registerSessionId, Long cashierId, CashierSessionStatus status);

    /** Used by the admin Shifts screen to show who worked a shift. */
    List<CashierSession> findByRegisterSessionIdOrderByStartedAtAsc(Long registerSessionId);

    /** Used by RegisterService.closeSession to end every still-open cashier session when the shift closes. */
    List<CashierSession> findByRegisterSessionIdAndStatus(Long registerSessionId, CashierSessionStatus status);

    /** Used by the "Sessions" screen for an ADMIN caller — every cashier session that has ever existed, across every shift. */
    List<CashierSession> findAllByOrderByStartedAtDesc();

    /** Used by the "Sessions" screen for a non-admin caller — just their own sessions, across every shift they've worked. */
    List<CashierSession> findAllByCashierIdOrderByStartedAtDesc(Long cashierId);
}
