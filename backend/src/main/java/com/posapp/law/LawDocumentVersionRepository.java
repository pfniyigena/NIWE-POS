package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LawDocumentVersionRepository extends JpaRepository<LawDocumentVersion, Long> {
    List<LawDocumentVersion> findByDocumentIdOrderByVersionNoDesc(Long documentId);

    Optional<LawDocumentVersion> findByDocumentIdAndVersionNo(Long documentId, int versionNo);
}
