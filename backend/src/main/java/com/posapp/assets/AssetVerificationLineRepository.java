package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetVerificationLineRepository extends JpaRepository<AssetVerificationLine, Long> {
    List<AssetVerificationLine> findByVerificationIdOrderByAssetCodeAsc(Long verificationId);

    long countByVerificationIdAndResult(Long verificationId, String result);

    long countByVerificationId(Long verificationId);
}
