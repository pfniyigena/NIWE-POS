package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReDealCommissionRepository extends JpaRepository<ReDealCommission, Long> {
    List<ReDealCommission> findByDealIdOrderByIdAsc(Long dealId);

    List<ReDealCommission> findByDealIdIn(Collection<Long> dealIds);
}
