package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestDocumentBlobRepository extends JpaRepository<GuestDocumentBlob, Long> {
    Optional<GuestDocumentBlob> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}
