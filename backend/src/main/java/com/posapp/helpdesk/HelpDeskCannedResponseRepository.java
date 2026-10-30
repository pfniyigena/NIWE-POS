package com.posapp.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpDeskCannedResponseRepository extends JpaRepository<HelpDeskCannedResponse, Long> {
    List<HelpDeskCannedResponse> findAllByOrderByTitleAsc();

    boolean existsByTitleIgnoreCase(String title);
}
