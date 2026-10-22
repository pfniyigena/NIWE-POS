package com.posapp.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CollectionReminderRepository extends JpaRepository<CollectionReminder, Long> {

    List<CollectionReminder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<CollectionReminder> findAllByOrderByCreatedAtDesc();

    List<CollectionReminder> findByCustomerIdAndKindInAndCreatedAtAfterOrderByCreatedAtDesc(Long customerId, List<String> kinds, Instant after);
}
