package com.posapp.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    List<SalesReturn> findAllByOrderByReturnDateDesc();

    /** Task 6: "Stock Movement History" — powers ReportService.stockMovementHistory. */
    List<SalesReturn> findByReturnDateBetweenOrderByReturnDateDesc(Instant from, Instant to);

    /**
     * `cashCodes` is whichever payment method codes are currently flagged cash-equivalent (see
     * PaymentMethodService.listCashCodes) — the caller must not invoke this with an empty
     * collection (an empty JPQL `in ()` is invalid); guard and return ZERO before calling.
     */
    @Query("select coalesce(sum(r.refundTotal), 0) from SalesReturn r " +
           "where r.refundMethod in :cashCodes and r.order.session.id = :sessionId")
    BigDecimal sumCashRefundsForSession(Long sessionId, Collection<String> cashCodes);
}
