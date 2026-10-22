package com.posapp.timetracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimeTimerRepository extends JpaRepository<TimeTimer, Long> {

    Optional<TimeTimer> findByUserId(Long userId);
}
