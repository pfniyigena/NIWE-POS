package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HkChecklistItemRepository extends JpaRepository<HkChecklistItem, Long> {

    java.util.List<HkChecklistItem> findAllByOrderBySortOrderAscIdAsc();

    java.util.List<HkChecklistItem> findByActiveTrueOrderBySortOrderAscIdAsc();
}
