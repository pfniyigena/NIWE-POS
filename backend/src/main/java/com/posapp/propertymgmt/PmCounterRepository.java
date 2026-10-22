package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PmCounterRepository extends JpaRepository<PmCounter, Long> {
    Optional<PmCounter> findByCounterKey(String counterKey);
}
