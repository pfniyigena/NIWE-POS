package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LawCostRateRepository extends JpaRepository<LawCostRate, Long> {
    Optional<LawCostRate> findByUserId(Long userId);
}
