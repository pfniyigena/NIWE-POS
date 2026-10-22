package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, Long> {
    List<RestaurantOrder> findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus status);

    /** Every order of this status at the table, oldest first — a table can have several checks open once a bill has been split. */
    List<RestaurantOrder> findByTableIdAndStatusOrderByIdAsc(Long tableId, RestaurantOrderStatus status);

    long countByParentOrderId(Long parentOrderId);

    /** Backs the Restaurant Receipts screen (lookup + reprint) — most recent checked-out orders, newest first, capped so the list stays fast. Same "top 200, CLOSED only" convention as OrderRepository.findTop200ByStatusOrderByCompletedAtDesc. */
    List<RestaurantOrder> findTop200ByStatusOrderByClosedAtDesc(RestaurantOrderStatus status);

    /** Backs the filtered, paged Receipts screen and its exports: every order in one status, newest first. */
    List<RestaurantOrder> findByStatusOrderByClosedAtDesc(RestaurantOrderStatus status);

    /** Backs ReportService.stockMovementHistory's "Restaurant sale"/"Restaurant ingredient consumption" rows — every order checked out (closedAt, not openedAt/createdAt) within the report's date range. */
    List<RestaurantOrder> findByStatusAndClosedAtBetweenOrderByClosedAtDesc(RestaurantOrderStatus status, Instant from, Instant to);
}
