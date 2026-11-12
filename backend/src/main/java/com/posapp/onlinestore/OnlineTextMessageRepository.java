package com.posapp.onlinestore;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface OnlineTextMessageRepository extends JpaRepository<OnlineTextMessage, Long> {
    long countByStatusAndCreatedAtAfter(String status, Instant since);

    Page<OnlineTextMessage> findAllByOrderByIdDesc(Pageable pageable);

    @Modifying
    @Query("delete from OnlineTextMessage m where m.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
