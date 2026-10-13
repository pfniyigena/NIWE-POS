package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxBandRepository extends JpaRepository<TaxBand, Long> {
    java.util.List<TaxBand> findAllByOrderBySortOrderAscFromAmountAsc();
}
