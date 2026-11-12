package com.posapp.onlinestore;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OnlineCouponRepository extends JpaRepository<OnlineCoupon, Long> {

    Optional<OnlineCoupon> findByCode(String code);

    /** Placing an order locks the coupon, so two orders cannot both take its last use. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OnlineCoupon c where c.code = :code")
    Optional<OnlineCoupon> lockByCode(@Param("code") String code);

    boolean existsByActiveTrue();

    List<OnlineCoupon> findAllByOrderByCreatedAtDesc();
}
