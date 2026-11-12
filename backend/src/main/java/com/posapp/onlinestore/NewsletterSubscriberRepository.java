package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {
    Optional<NewsletterSubscriber> findByEmail(String email);

    Optional<NewsletterSubscriber> findByConfirmHash(String hash);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String token);

    long countByStatus(String status);

    List<NewsletterSubscriber> findAllByOrderByIdDesc();

    List<NewsletterSubscriber> findByStatusOrderByIdDesc(String status);
}
