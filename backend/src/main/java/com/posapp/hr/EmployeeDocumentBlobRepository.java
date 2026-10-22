package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeDocumentBlobRepository extends JpaRepository<EmployeeDocumentBlob, Long> {
    Optional<EmployeeDocumentBlob> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}
