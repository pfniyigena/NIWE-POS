package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawDocTemplateRepository extends JpaRepository<LawDocTemplate, Long> {
    List<LawDocTemplate> findAllByOrderByNameAsc();
}
