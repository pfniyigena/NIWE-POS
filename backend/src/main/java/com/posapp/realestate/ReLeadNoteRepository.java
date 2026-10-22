package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReLeadNoteRepository extends JpaRepository<ReLeadNote, Long> {
    List<ReLeadNote> findByLeadIdOrderByIdDesc(Long leadId);
}
