package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MinibarItemRepository extends JpaRepository<MinibarItem, Long> {

    java.util.List<MinibarItem> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
