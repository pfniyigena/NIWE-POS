package com.posapp.promotions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PromotionUseRepository extends JpaRepository<PromotionUse, Long> {
    long countByPromotionId(Long promotionId);
    long countByPromotionIdAndCustomerId(Long promotionId, Long customerId);
    List<PromotionUse> findByPromotionIdOrderByIdDesc(Long promotionId);

    @Query("select u.promotionId, count(u), coalesce(sum(u.amount), 0) from PromotionUse u group by u.promotionId")
    List<Object[]> totals();

    @Query("select u.promotionId, count(u) from PromotionUse u where u.customerId = :customerId group by u.promotionId")
    List<Object[]> countsForCustomer(Long customerId);
}
