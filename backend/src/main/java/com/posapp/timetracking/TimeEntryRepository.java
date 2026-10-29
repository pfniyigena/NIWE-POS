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

    /** Minutes logged per project (for budget checks), as {projectId, minutes} rows. */
    @org.springframework.data.jpa.repository.Query("select e.projectId, sum(e.minutes) from TimeEntry e group by e.projectId")
    List<Object[]> minutesByProject();

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.minutes), 0) from TimeEntry e where e.projectId = :projectId")
    long minutesOfProject(@org.springframework.data.repository.query.Param("projectId") Long projectId);
}
