package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetCustodyRepository extends JpaRepository<AssetCustody, Long> {
    List<AssetCustody> findByAssetIdOrderByCheckedOutOnDescIdDesc(Long assetId);

    Optional<AssetCustody> findFirstByAssetIdAndCheckedInOnIsNullOrderByIdDesc(Long assetId);
}
