package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LawMatterPortalRepository extends JpaRepository<LawMatterPortal, Long> {
    Optional<LawMatterPortal> findByMatterId(Long matterId);
}
