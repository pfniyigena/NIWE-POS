package com.posapp.onlinestore;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShopLoyaltyEntryRepository extends JpaRepository<ShopLoyaltyEntry, Long> {
    List<ShopLoyaltyEntry> findByShopCustomerIdOrderByIdDesc(Long shopCustomerId, Pageable page);

    boolean existsByOnlineOrderIdAndKindAndOnlineReturnIdIsNull(Long orderId, String kind);

    boolean existsByOnlineReturnIdAndKind(Long returnId, String kind);

    /** What an order already took back or gave back through its returns (positive numbers), by kind. */
    @Query("select coalesce(sum(abs(e.points)), 0) from ShopLoyaltyEntry e where e.onlineOrderId = :orderId and e.kind = :kind")
    long sumByOrderAndKind(@Param("orderId") Long orderId, @Param("kind") String kind);

    @Modifying
    @Query("delete from ShopLoyaltyEntry e where e.shopCustomerId = :id")
    int deleteForCustomer(@Param("id") Long customerId);
}
