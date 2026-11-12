package com.posapp.onlinestore;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OnlinePaymentRepository extends JpaRepository<OnlinePayment, Long> {
    Optional<OnlinePayment> findByReference(String reference);

    /** The row is locked until the transaction ends, so a webhook and the status check cannot both settle the same payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OnlinePayment p where p.reference = :reference")
    Optional<OnlinePayment> lockByReference(@Param("reference") String reference);

    List<OnlinePayment> findByOnlineOrderIdOrderByIdDesc(Long orderId);

    long countByOnlineOrderId(Long orderId);

    @Query("select p.id from OnlinePayment p where p.status = 'PENDING' order by p.id")
    List<Long> pendingIds();

    @Query("select p from OnlinePayment p where p.createdAt >= :from and p.createdAt < :to order by p.id desc")
    List<OnlinePayment> between(@Param("from") Instant from, @Param("to") Instant to);

    /** Payments a person has to look at, whatever their date: under review, money to give back, or pending long after they should have closed. */
    @Query("select p from OnlinePayment p where p.status = 'REVIEW' or p.onlineOrder.paymentStatus = 'REFUND_DUE' or (p.status = 'PENDING' and p.expiresAt < :cutoff)")
    List<OnlinePayment> needAttention(@Param("cutoff") Instant cutoff);

    long countByStatus(String status);
}
