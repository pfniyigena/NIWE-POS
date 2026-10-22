package com.posapp.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerGroupRepository extends JpaRepository<CustomerGroup, Long> {
    List<CustomerGroup> findAllByOrderByNameAsc();
    Optional<CustomerGroup> findByNameIgnoreCase(String name);
    long countByPriceListId(Long priceListId);
}
