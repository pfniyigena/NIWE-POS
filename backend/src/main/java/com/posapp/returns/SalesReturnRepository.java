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
    @Query("select coalesce(sum(r.refundTotal - r.giftCardRefund - r.loyaltyRefund), 0) from SalesReturn r " +
           "where r.refundMethod in :cashCodes and r.order.session.id = :sessionId")
    BigDecimal sumCashRefundsForSession(Long sessionId, Collection<String> cashCodes);

    /** Money already given back as loyalty points on earlier returns of one sale (not counting the return being processed). */
    @Query("select coalesce(sum(r.loyaltyRefund), 0) from SalesReturn r where r.order.id = :orderId and r.id <> :exceptReturnId")
    BigDecimal sumLoyaltyRefundForOrder(Long orderId, Long exceptReturnId);

    @Query("select coalesce(sum(r.pointsRefund), 0) from SalesReturn r where r.order.id = :orderId and r.id <> :exceptReturnId")
    Number sumPointsRefundForOrder(Long orderId, Long exceptReturnId);
}
