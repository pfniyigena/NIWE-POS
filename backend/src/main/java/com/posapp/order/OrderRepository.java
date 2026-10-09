package com.posapp.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<SaleOrder, Long> {

    /** Used for shift-wide aggregation (till totals, Z-reports) — spans every cashier session on the shift. */
    List<SaleOrder> findBySessionIdAndStatusOrderByCreatedAtAsc(Long sessionId, OrderStatus status);

    /** Used for the terminal's own tab bar — scoped to one cashier's working session, not the whole shift, so cashiers sharing a till don't see each other's open carts. */
    List<SaleOrder> findByCashierSessionIdAndStatusOrderByCreatedAtAsc(Long cashierSessionId, OrderStatus status);

    /** Every order ever placed under one cashier's own session, any status — the cashier's own order history (not just currently-open tabs), shown from the "Sessions" screen. */
    List<SaleOrder> findByCashierSessionIdOrderByCreatedAtDesc(Long cashierSessionId);

    Optional<SaleOrder> findByInvoiceNumber(String invoiceNumber);

    /** Idempotency check for offline-queued sales — see OrderService.syncOfflineSale and SaleOrder.clientTempId's javadoc. */
    Optional<SaleOrder> findByClientTempId(String clientTempId);

    long countByInvoiceNumberIsNotNull();

    /** Used by DashboardService for "today's sales" — completedAt is only set once an order is COMPLETED. */
    List<SaleOrder> findByStatusAndCompletedAtGreaterThanEqual(OrderStatus status, Instant from);

    /** Used by the "Receipts" screen — most recent completed sales, newest first, capped so the list stays fast. */
    List<SaleOrder> findTop200ByStatusOrderByCompletedAtDesc(OrderStatus status);

    /** Same, scoped to one till session — used when Receipts is opened from inside the terminal. */
    List<SaleOrder> findTop200BySessionIdAndStatusOrderByCompletedAtDesc(Long sessionId, OrderStatus status);

    /**
     * Backs every POS Report (see the {@code reports} package) — every completed sale in a date
     * range, optionally narrowed to one register's shifts, oldest first. {@code registerId} is a
     * plain nullable Long so the same "is null or" pattern ProductRepository.search already uses
     * for categoryId is safe here too (that pattern is specifically to avoid a Postgres/JDBC null
     * type-inference issue with Strings, which doesn't apply to Long).
     */
    @Query("select o from SaleOrder o where o.status = :status " +
           "and o.completedAt >= :from and o.completedAt <= :to " +
           "and (:registerId is null or o.session.register.id = :registerId) " +
           "order by o.completedAt asc")
    List<SaleOrder> findCompletedForReport(@Param("status") OrderStatus status,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to,
                                            @Param("registerId") Long registerId);

    /**
     * Backs the Customer Statement screen (Task: "Add Customers Report (Customer Statement)") —
     * every completed sale for one specific customer in a date range, oldest first. A sibling of
     * {@link #findCompletedForReport} filtered by customer instead of register — kept as its own
     * query rather than adding an optional {@code customerId} param there, since this one always
     * requires a customer (there's no "all customers" statement) where the POS Reports' register
     * filter is always optional.
     */
    @Query("select o from SaleOrder o where o.customer.id = :customerId and o.status = :status " +
           "and o.completedAt >= :from and o.completedAt <= :to order by o.completedAt asc")
    List<SaleOrder> findCompletedForCustomerStatement(@Param("customerId") Long customerId,
                                                        @Param("status") OrderStatus status,
                                                        @Param("from") Instant from,
                                                        @Param("to") Instant to);

    /**
     * Every completed sale for one customer that has a credit portion, oldest first (Task:
     * "Payment allocation to orders" — backs the Record Payment screen's allocation table, and
     * CustomerCreditService's outstanding-balance sum). Oldest-first ordering is what the
     * frontend's default allocation suggestion relies on.
     */
    @Query("select o from SaleOrder o where o.customer.id = :customerId and o.status = :status " +
           "and o.creditAmount is not null and o.creditAmount > 0 order by o.completedAt asc")
    List<SaleOrder> findCreditOrdersForCustomer(@Param("customerId") Long customerId,
                                                  @Param("status") OrderStatus status);

    /**
     * Every completed credit sale across all customers in a date range, oldest first — backs the
     * Credit Sale report and Daily Credit Collection report (Tasks 16/17).
     */
    @Query("select o from SaleOrder o where o.status = :status " +
           "and o.creditAmount is not null and o.creditAmount > 0 " +
           "and o.completedAt >= :from and o.completedAt <= :to order by o.completedAt asc")
    List<SaleOrder> findAllCreditOrders(@Param("status") OrderStatus status,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    /**
     * Every completed credit sale ever, for the Outstanding/Overdue Credit reports (Tasks 10/11),
     * which are snapshot-style (no date range) — every currently-outstanding order regardless of
     * when it was made.
     */
    @Query("select o from SaleOrder o where o.status = :status " +
           "and o.creditAmount is not null and o.creditAmount > 0 order by o.completedAt asc")
    List<SaleOrder> findAllCreditOrders(@Param("status") OrderStatus status);

    /**
     * Every completed, customer-attached sale, newest first — every one of these orders' own
     * {@code payments} rows is a checkout tender that Customer Payment history now merges in
     * alongside actual {@code CustomerPayment} settlements (Fourier's own follow-up: "On Customer
     * payment list no POS payment for cash sale"). Store-wide counterpart of {@link
     * #findCompletedForCustomer}; see {@code CustomerCreditService.paymentHistory()}.
     */
    @Query("select o from SaleOrder o where o.status = :status and o.customer is not null " +
           "order by o.completedAt desc")
    List<SaleOrder> findAllCompletedWithCustomer(@Param("status") OrderStatus status);

    /**
     * Same as {@link #findAllCompletedWithCustomer} but scoped to one customer, newest first —
     * backs the per-customer history table on the Record Payment screen. See {@code
     * CustomerCreditService.paymentHistory(Long)}.
     */
    @Query("select o from SaleOrder o where o.status = :status and o.customer.id = :customerId " +
           "order by o.completedAt desc")
    List<SaleOrder> findCompletedForCustomer(@Param("status") OrderStatus status,
                                               @Param("customerId") Long customerId);
}
