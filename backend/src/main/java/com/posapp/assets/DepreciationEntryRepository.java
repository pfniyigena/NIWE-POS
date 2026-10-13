package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DepreciationEntryRepository extends JpaRepository<DepreciationEntry, Long> {
    List<DepreciationEntry> findByAssetIdOrderByPeriodEndAsc(Long assetId);

    List<DepreciationEntry> findByPeriodEndBetweenOrderByPeriodEndAscIdAsc(LocalDate from, LocalDate to);

    boolean existsByAssetId(Long assetId);
}
