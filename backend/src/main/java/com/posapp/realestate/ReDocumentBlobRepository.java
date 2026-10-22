package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReDocumentBlobRepository extends JpaRepository<ReDocumentBlob, Long> {
    Optional<ReDocumentBlob> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}
