package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnlineListingRepository extends JpaRepository<OnlineListing, Long> {
    List<OnlineListing> findAllByOrderBySortOrderAscIdAsc();

    List<OnlineListing> findByListedTrueOrderBySortOrderAscIdAsc();

    Optional<OnlineListing> findByProductId(Long productId);
}
