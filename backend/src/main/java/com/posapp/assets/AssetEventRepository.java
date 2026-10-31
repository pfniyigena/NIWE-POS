package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetEventRepository extends JpaRepository<AssetEvent, Long> {
    List<AssetEvent> findByAssetIdOrderByIdDesc(Long assetId);
}
