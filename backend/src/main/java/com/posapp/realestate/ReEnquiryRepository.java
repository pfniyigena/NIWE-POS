package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ReEnquiryRepository extends JpaRepository<ReEnquiry, Long> {
    long countByContactKeyAndCreatedAtAfter(String contactKey, Instant after);
}
