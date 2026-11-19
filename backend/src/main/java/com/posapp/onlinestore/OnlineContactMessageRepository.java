package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnlineContactMessageRepository extends JpaRepository<OnlineContactMessage, Long> {
    long countByStatus(String status);

    List<OnlineContactMessage> findAllByOrderByIdDesc();

    List<OnlineContactMessage> findByStatusOrderByIdDesc(String status);
}
