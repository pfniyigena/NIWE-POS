package com.posapp.timetracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimeProjectRateRepository extends JpaRepository<TimeProjectRate, Long> {

    List<TimeProjectRate> findByProjectId(Long projectId);

    Optional<TimeProjectRate> findByProjectIdAndUserId(Long projectId, Long userId);

    void deleteByProjectId(Long projectId);
}
