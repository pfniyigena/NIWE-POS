package com.posapp.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {
    List<PriceList> findAllByOrderByNameAsc();
    Optional<PriceList> findByNameIgnoreCase(String name);
}
