package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopWishRepository extends JpaRepository<ShopWish, Long> {
    List<ShopWish> findByShopCustomerIdOrderByIdDesc(Long shopCustomerId);

    Optional<ShopWish> findByShopCustomerIdAndProductId(Long shopCustomerId, Long productId);

    long countByShopCustomerId(Long shopCustomerId);

    @Modifying
    @Query("delete from ShopWish w where w.shopCustomerId = :customerId")
    void deleteForCustomer(@Param("customerId") Long customerId);
}
