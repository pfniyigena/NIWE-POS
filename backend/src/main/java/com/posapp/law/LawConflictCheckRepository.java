package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawConflictCheckRepository extends JpaRepository<LawConflictCheck, Long> {
    List<LawConflictCheck> findTop100ByOrderByCreatedAtDesc();
}
