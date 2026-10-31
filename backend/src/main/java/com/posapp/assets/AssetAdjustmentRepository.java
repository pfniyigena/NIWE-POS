package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AssetAdjustmentRepository extends JpaRepository<AssetAdjustment, Long> {
    List<AssetAdjustment> findByAssetIdOrderByAdjustmentDateDescIdDesc(Long assetId);

    List<AssetAdjustment> findByAssetIdOrderByAdjustmentDateAscIdAsc(Long assetId);

    List<AssetAdjustment> findByAdjustmentDateBetweenOrderByAdjustmentDateAscIdAsc(LocalDate from, LocalDate to);

    List<AssetAdjustment> findAllByOrderByAdjustmentDateAscIdAsc();
}
