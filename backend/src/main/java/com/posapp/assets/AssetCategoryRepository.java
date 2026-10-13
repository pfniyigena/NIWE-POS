package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {
    List<AssetCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
