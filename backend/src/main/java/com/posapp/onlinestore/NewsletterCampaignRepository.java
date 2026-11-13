package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsletterCampaignRepository extends JpaRepository<NewsletterCampaign, Long> {
    List<NewsletterCampaign> findAllByOrderByIdDesc();

    List<NewsletterCampaign> findByStatusOrderByIdAsc(String status);
}
