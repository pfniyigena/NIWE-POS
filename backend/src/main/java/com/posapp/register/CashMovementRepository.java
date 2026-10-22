package com.posapp.register;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    List<CashMovement> findByRegisterSessionIdOrderByCreatedAtAsc(Long registerSessionId);

    List<CashMovement> findAllByOrderByCreatedAtDesc();
}
