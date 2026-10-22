package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PmDocumentBlobRepository extends JpaRepository<PmDocumentBlob, Long> {
    Optional<PmDocumentBlob> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}
