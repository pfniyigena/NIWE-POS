package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RestaurantOptionGroupRepository extends JpaRepository<RestaurantOptionGroup, Long> {
    List<RestaurantOptionGroup> findByMenuItemIdOrderBySortOrderAscIdAsc(Long menuItemId);

    List<RestaurantOptionGroup> findByMenuItemIdInOrderBySortOrderAscIdAsc(Collection<Long> menuItemIds);
}
