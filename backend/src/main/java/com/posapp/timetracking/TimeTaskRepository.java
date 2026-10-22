package com.posapp.timetracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeTaskRepository extends JpaRepository<TimeTask, Long> {

    List<TimeTask> findByProjectIdOrderByNameAsc(Long projectId);

    List<TimeTask> findByActiveTrueOrderByNameAsc();
}
