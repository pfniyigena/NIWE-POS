package com.posapp.timetracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    List<TimeEntry> findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(Long userId, LocalDate from, LocalDate to);

    List<TimeEntry> findByWorkDateBetweenOrderByWorkDateAscIdAsc(LocalDate from, LocalDate to);

    List<TimeEntry> findByStatusOrderByWorkDateAscIdAsc(TimeEntryStatus status);

    List<TimeEntry> findByProjectId(Long projectId);

    List<TimeEntry> findByInvoiceId(Long invoiceId);

    List<TimeEntry> findByStatus(TimeEntryStatus status);

    List<TimeEntry> findByTaskId(Long taskId);

    long countByProjectId(Long projectId);

    long countByTaskId(Long taskId);

    List<TimeEntry> findByUserIdAndWorkDate(Long userId, LocalDate date);

    List<TimeEntry> findByIdIn(Collection<Long> ids);
}
