package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GroupBlockRepository extends JpaRepository<GroupBlock, Long> {
    List<GroupBlock> findByGroupIdOrderByStartDateAscIdAsc(Long groupId);

    /** Blocks of a room type that overlap [from, to) and are not released by hand (status and release date are checked in code). */
    @Query("select b from GroupBlock b where b.released = false and b.roomType.id = :typeId and b.startDate < :to and b.endDate > :from")
    List<GroupBlock> findCandidates(@Param("typeId") Long typeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select b from GroupBlock b where b.released = false and b.startDate < :to and b.endDate > :from")
    List<GroupBlock> findCandidatesAll(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
