package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReCounterRepository extends JpaRepository<ReCounter, Long> {
    Optional<ReCounter> findByCounterKey(String counterKey);
}
