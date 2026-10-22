package com.posapp.controls;

import com.posapp.accounting.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpeningEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findBySourceTypeOrderByIdDesc(String sourceType);

    Optional<JournalEntry> findFirstBySourceTypeAndSourceId(String sourceType, Long sourceId);
}
