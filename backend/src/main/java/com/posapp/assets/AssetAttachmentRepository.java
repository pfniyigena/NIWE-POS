package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssetAttachmentRepository extends JpaRepository<AssetAttachment, Long> {
    List<AssetAttachment> findByAssetIdOrderByIdAsc(Long assetId);

    long countByAssetId(Long assetId);

    @Query("select a.assetId, count(a) from AssetAttachment a group by a.assetId")
    List<Object[]> countsByAsset();
}
