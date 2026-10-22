package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawMatterNoteRepository extends JpaRepository<LawMatterNote, Long> {
    List<LawMatterNote> findByMatterIdOrderByCreatedAtDesc(Long matterId);

    long countByMatterId(Long matterId);

    void deleteByMatterId(Long matterId);
}
