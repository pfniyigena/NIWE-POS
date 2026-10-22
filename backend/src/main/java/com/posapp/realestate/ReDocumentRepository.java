package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReDocumentRepository extends JpaRepository<ReDocument, Long> {
    List<ReDocument> findBySubjectTypeAndSubjectIdOrderByIdDesc(DocSubject type, Long id);

    long countBySubjectTypeAndSubjectId(DocSubject type, Long id);

    List<ReDocument> findBySubjectTypeAndSubjectIdIn(DocSubject type, Collection<Long> ids);
}
