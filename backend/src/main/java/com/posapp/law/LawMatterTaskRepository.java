package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawMatterTaskRepository extends JpaRepository<LawMatterTask, Long> {
    List<LawMatterTask> findByMatterIdOrderByIdAsc(Long matterId);

    List<LawMatterTask> findByStatusNot(TaskStatus status);

    long countByMatterId(Long matterId);

    void deleteByMatterId(Long matterId);
}
