package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AssetTransferRepository extends JpaRepository<AssetTransfer, Long> {
    List<AssetTransfer> findByAssetIdOrderByTransferDateDescIdDesc(Long assetId);

    List<AssetTransfer> findByTransferDateBetweenOrderByTransferDateAscIdAsc(LocalDate from, LocalDate to);
}
