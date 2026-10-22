package com.posapp.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LoyaltyTxnRepository extends JpaRepository<LoyaltyTxn, Long> {

    List<LoyaltyTxn> findByCustomerIdOrderByIdDesc(Long customerId);

    @Query("select coalesce(sum(t.points), 0) from LoyaltyTxn t where t.customerId = :customerId")
    Number balanceOf(Long customerId);

    /** Points of one kind already booked against one sale (used to stop a return taking back more than was earned). */
    @Query("select coalesce(sum(t.points), 0) from LoyaltyTxn t where t.orderId = :orderId and t.type = :type")
    Number sumForOrder(Long orderId, LoyaltyTxnType type);

    /** One row per customer who has any points activity: id, balance, last activity. */
    @Query("select t.customerId, sum(t.points), max(t.createdAt) from LoyaltyTxn t group by t.customerId")
    List<Object[]> customerTotals();
}
