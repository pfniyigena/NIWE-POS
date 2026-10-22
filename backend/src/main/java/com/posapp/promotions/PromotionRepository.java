package com.posapp.promotions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findAllByOrderByNameAsc();
    Optional<Promotion> findByNameIgnoreCase(String name);
    Optional<Promotion> findByCouponCodeIgnoreCase(String couponCode);
}
