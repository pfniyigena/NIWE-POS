package com.posapp.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetVerificationRepository extends JpaRepository<AssetVerification, Long> {
    List<AssetVerification> findAllByOrderByIdDesc();

    long countByStatus(String status);
}
