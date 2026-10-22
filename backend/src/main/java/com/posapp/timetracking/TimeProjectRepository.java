package com.posapp.timetracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimeProjectRepository extends JpaRepository<TimeProject, Long> {

    List<TimeProject> findAllByOrderByNameAsc();

    List<TimeProject> findByStatusOrderByNameAsc(ProjectStatus status);

    Optional<TimeProject> findFirstBySourceTypeAndSourceId(String sourceType, Long sourceId);
}
