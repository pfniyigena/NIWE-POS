package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LawDocumentBlobRepository extends JpaRepository<LawDocumentBlob, Long> {
    Optional<LawDocumentBlob> findByVersionId(Long versionId);
}
