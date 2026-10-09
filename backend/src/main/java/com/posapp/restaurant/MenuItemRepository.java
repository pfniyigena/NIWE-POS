package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findAllByOrderBySortOrderAsc();

    List<MenuItem> findByCategoryIdOrderBySortOrderAsc(Long categoryId);

    boolean existsByCategoryIdAndProductId(Long categoryId, Long productId);
}
