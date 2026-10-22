package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RestaurantWaitlistRepository extends JpaRepository<RestaurantWaitlist, Long> {
    List<RestaurantWaitlist> findAllByOrderByCreatedAtAscIdAsc();

    List<RestaurantWaitlist> findByStatusInOrderByCreatedAtAscIdAsc(Collection<String> statuses);
}
