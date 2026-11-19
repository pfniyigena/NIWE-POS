package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnlineInfoPageRepository extends JpaRepository<OnlineInfoPage, Long> {
    Optional<OnlineInfoPage> findBySlug(String slug);
}
