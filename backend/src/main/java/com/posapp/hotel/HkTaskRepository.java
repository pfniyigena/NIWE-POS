package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HkTaskRepository extends JpaRepository<HkTask, Long> {
    List<HkTask> findByTaskDateOrderByIdAsc(LocalDate date);

    List<HkTask> findByTaskDateBetweenOrderByTaskDateAscIdAsc(LocalDate from, LocalDate to);

    Optional<HkTask> findByRoomIdAndTaskDateAndKind(Long roomId, LocalDate date, HkTaskKind kind);

    List<HkTask> findByRoomIdAndTaskDateOrderByIdAsc(Long roomId, LocalDate date);

    long countByStaffId(Long staffId);
}
