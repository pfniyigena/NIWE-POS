package com.posapp.register;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegisterSessionRepository extends JpaRepository<RegisterSession, Long> {

    Optional<RegisterSession> findByRegisterIdAndStatus(Long registerId, SessionStatus status);

    List<RegisterSession> findAllByOrderByOpenedAtDesc();

    List<RegisterSession> findByStatusOrderByOpenedAtDesc(SessionStatus status);

    long countByStatus(SessionStatus status);
}
