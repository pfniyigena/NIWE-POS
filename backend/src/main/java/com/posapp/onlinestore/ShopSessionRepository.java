package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShopSessionRepository extends JpaRepository<ShopSession, Long> {
    Optional<ShopSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from ShopSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from ShopSession s where s.shopCustomerId = :id")
    int deleteForCustomer(@Param("id") Long id);
}
