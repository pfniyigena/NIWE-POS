package com.posapp.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpDeskCategoryRepository extends JpaRepository<HelpDeskCategory, Long> {
    boolean existsByNameIgnoreCase(String name);

    List<HelpDeskCategory> findAllByOrderByNameAsc();

    List<HelpDeskCategory> findByActiveTrueOrderByNameAsc();
}
