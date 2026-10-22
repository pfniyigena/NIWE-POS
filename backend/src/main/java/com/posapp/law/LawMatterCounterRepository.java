package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LawMatterCounterRepository extends JpaRepository<LawMatterCounter, Long> {
    Optional<LawMatterCounter> findByCounterKey(String counterKey);
}
