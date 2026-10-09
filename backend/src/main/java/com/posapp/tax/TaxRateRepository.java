package com.posapp.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<TaxRate> findByCodeIgnoreCase(String code);

    List<TaxRate> findByActiveTrueOrderByNameAsc();

    List<TaxRate> findAllByOrderByNameAsc();
}
