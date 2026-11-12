package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, Long> {
    Optional<OnlineOrder> findByCode(String code);

    List<OnlineOrder> findAllByOrderByCreatedAtDesc();

    List<OnlineOrder> findByStatusOrderByCreatedAtAsc(String status);

    long countByStatus(String status);

    long countByStatusAndCreatedAtBefore(String status, Instant before);

    /** Orders still waiting for an answer that the shop has not been told about yet. */
    List<OnlineOrder> findByStatusAndAlertedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(String status, Instant before);

    List<OnlineOrder> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(String status, Instant before);

    long countByPaymentStatus(String paymentStatus);

    long countByCustomerPhoneAndStatus(String customerPhone, String status);

    long countByCreatedAtAfter(Instant since);
}
