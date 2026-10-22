package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GroupEntryRepository extends JpaRepository<GroupEntry, Long> {
    List<GroupEntry> findByGroupIdOrderByIdAsc(Long groupId);

    boolean existsByGroupId(Long groupId);

    @Query("select e.kind, coalesce(sum(e.amount), 0) from GroupEntry e group by e.kind")
    List<Object[]> totalsByKind();

    List<GroupEntry> findByEntryDate(java.time.LocalDate date);
}
