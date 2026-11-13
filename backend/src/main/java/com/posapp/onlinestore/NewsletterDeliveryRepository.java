package com.posapp.onlinestore;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NewsletterDeliveryRepository extends JpaRepository<NewsletterDelivery, Long> {
    @Query("select d.status, count(d) from NewsletterDelivery d where d.campaignId = :id group by d.status")
    List<Object[]> counts(@Param("id") Long campaignId);

    List<NewsletterDelivery> findByCampaignIdAndStatusOrderByIdAsc(Long campaignId, String status, Pageable page);

    List<NewsletterDelivery> findByCampaignIdAndStatusOrderByIdAsc(Long campaignId, String status);

    List<NewsletterDelivery> findByCampaignIdOrderByIdAsc(Long campaignId);

    long countByCampaignIdAndStatus(Long campaignId, String status);

    long countByStatusAndSentAtAfter(String status, Instant after);

    List<NewsletterDelivery> findByStatusAndUpdatedAtBefore(String status, Instant before);

    @Modifying
    @Query("update NewsletterDelivery d set d.status = 'PENDING', d.error = null where d.campaignId = :id and d.status = 'FAILED'")
    int retryFailed(@Param("id") Long campaignId);
}
