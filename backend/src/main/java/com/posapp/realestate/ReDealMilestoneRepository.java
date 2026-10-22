package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReDealMilestoneRepository extends JpaRepository<ReDealMilestone, Long> {
    List<ReDealMilestone> findByDealIdOrderBySortOrderAscIdAsc(Long dealId);

    List<ReDealMilestone> findByDealIdIn(Collection<Long> dealIds);
}
