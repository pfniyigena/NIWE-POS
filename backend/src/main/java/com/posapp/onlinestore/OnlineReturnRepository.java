package com.posapp.onlinestore;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OnlineReturnRepository extends JpaRepository<OnlineReturn, Long> {
    List<OnlineReturn> findByOnlineOrderIdOrderByIdAsc(Long onlineOrderId);

    List<OnlineReturn> findAllByOrderByCreatedAtDesc();

    long countByOnlineOrderId(Long onlineOrderId);

    long countByStatus(String status);

    /** Staff decide or process under a lock, so two people cannot do it twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OnlineReturn r where r.id = :id")
    Optional<OnlineReturn> lockById(@Param("id") Long id);
}
