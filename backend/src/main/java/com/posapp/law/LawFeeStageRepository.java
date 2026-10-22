package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawFeeStageRepository extends JpaRepository<LawFeeStage, Long> {
    List<LawFeeStage> findByMatterIdOrderByIdAsc(Long matterId);

    List<LawFeeStage> findByBillId(Long billId);

    long countByMatterId(Long matterId);

    void deleteByMatterId(Long matterId);
}
