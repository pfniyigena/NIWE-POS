package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnlinePaymentWebhookRepository extends JpaRepository<OnlinePaymentWebhook, Long> {
    List<OnlinePaymentWebhook> findTop50ByReferenceOrderByIdDesc(String reference);
}
