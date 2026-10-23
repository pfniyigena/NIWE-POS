package com.posapp.theme;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserThemeRepository extends JpaRepository<UserTheme, Long> {
    Optional<UserTheme> findByAppUserId(Long appUserId);
}
