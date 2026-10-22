package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PmDocumentRepository extends JpaRepository<PmDocument, Long> {
    List<PmDocument> findBySubjectTypeAndSubjectIdOrderByIdDesc(PmDocSubject type, Long id);

    long countBySubjectTypeAndSubjectId(PmDocSubject type, Long id);

    long countByExpiryDateBefore(LocalDate date);

    long countByExpiryDateBetween(LocalDate from, LocalDate to);
}
