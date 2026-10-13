package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, Long> {
    List<FixedAsset> findAllByOrderByCodeAsc();

    List<FixedAsset> findByStatusOrderByCodeAsc(AssetStatus status);

    boolean existsByCode(String code);

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    long countByPurchaseOrderLineId(Long purchaseOrderLineId);

    List<FixedAsset> findByStatusAndDisposalDateBetweenOrderByDisposalDateAsc(AssetStatus status, LocalDate from, LocalDate to);
}
