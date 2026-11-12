package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopCustomerRepository extends JpaRepository<ShopCustomer, Long> {
    Optional<ShopCustomer> findByEmail(String email);

    List<ShopCustomer> findByPhone(String phone);
}
