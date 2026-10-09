package com.posapp.salesorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findAllByOrderByOrderDateDesc();

    /**
     * Every Sales Order ever placed by one customer whose status carries an amount owed
     * (CONFIRMED/PARTIALLY_FULFILLED/FULFILLED — never DRAFT/CANCELLED), oldest first — the sales
     * mirror of {@code OrderRepository.findCreditOrdersForCustomer}, read directly by {@code
     * CustomerCreditService} so a Sales Order's outstanding amount can be merged into the same
     * outstanding-balance/allocation-table/report math a POS credit sale already goes through.
     */
    List<SalesOrder> findByCustomerIdAndStatusInOrderByOrderDateAsc(Long customerId, List<SalesOrderStatus> statuses);

    /** Task: "Add Sale order reports" — a plain date-ranged listing, same shape as PurchaseOrderRepository's report query. */
    List<SalesOrder> findByOrderDateBetweenOrderByOrderDateDesc(Instant from, Instant to);

    /** Guards SupplierService-style delete-vs-deactivate decisions if ever needed; unused today but mirrors PurchaseOrderRepository.existsBySupplierId for symmetry. */
    boolean existsByCustomerId(Long customerId);

    /**
     * Every Sales Order across every customer whose status carries an amount still owed/undelivered
     * (Fourier's Manufacturing Module "Production plans" — {@code
     * com.posapp.manufacturing.ProductionPlanService.generate} aggregates unfulfilled quantity from
     * these to compute manufacturing demand for a finished good). Store-wide counterpart of {@link
     * #findByCustomerIdAndStatusInOrderByOrderDateAsc}.
     */
    List<SalesOrder> findByStatusIn(List<SalesOrderStatus> statuses);
}
