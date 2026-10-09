package com.posapp.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long> {

    /** One customer's payment history (Task: "Payment history"), newest first. */
    List<CustomerPayment> findByCustomerIdOrderByPaidAtDesc(Long customerId);

    /** Store-wide payment history, across every customer — backs the new Customer Payments list screen. */
    List<CustomerPayment> findAllByOrderByPaidAtDesc();

    /** Backs the Daily Credit Collection report (Task: "Daily credit collection report") — every payment recorded in a date range, oldest first so grouping-by-day accumulates in order. */
    List<CustomerPayment> findByPaidAtBetweenOrderByPaidAtAsc(Instant from, Instant to);
}
