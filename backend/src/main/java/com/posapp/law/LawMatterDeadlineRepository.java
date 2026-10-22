package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawMatterDeadlineRepository extends JpaRepository<LawMatterDeadline, Long> {
    List<LawMatterDeadline> findByMatterIdOrderByDueDateAscDueTimeAsc(Long matterId);

    List<LawMatterDeadline> findByStatus(DeadlineStatus status);

    long countByMatterId(Long matterId);

    void deleteByMatterId(Long matterId);
}
