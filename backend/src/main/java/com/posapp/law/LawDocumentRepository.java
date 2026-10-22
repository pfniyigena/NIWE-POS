package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawDocumentRepository extends JpaRepository<LawDocument, Long> {
    List<LawDocument> findByMatterIdOrderByUpdatedAtDesc(Long matterId);

    long countByMatterId(Long matterId);

    long countByTemplateId(Long templateId);
}
