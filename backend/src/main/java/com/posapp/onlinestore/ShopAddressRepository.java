package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopAddressRepository extends JpaRepository<ShopAddress, Long> {
    List<ShopAddress> findByShopCustomerIdOrderByDefaultAddressDescIdAsc(Long shopCustomerId);

    Optional<ShopAddress> findByIdAndShopCustomerId(Long id, Long shopCustomerId);

    long countByShopCustomerId(Long shopCustomerId);

    @Modifying
    @Query("delete from ShopAddress a where a.shopCustomerId = :customerId")
    void deleteForCustomer(@Param("customerId") Long customerId);
}
