package com.posapp.onlinestore;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ShopLoginCodeRepository extends JpaRepository<ShopLoginCode, Long> {
    /** The newest codes of an address first, locked, so two verifications at once cannot both count or both use the same code. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShopLoginCode c where c.email = :email and c.used = false order by c.id desc")
    List<ShopLoginCode> lockOpen(@Param("email") String email);

    long countByEmailAndCreatedAtAfter(String email, Instant since);

    @Modifying
    @Query("delete from ShopLoginCode c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
