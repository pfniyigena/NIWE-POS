package com.posapp.packaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackagingRuleRepository extends JpaRepository<PackagingRule, Long> {

    List<PackagingRule> findAllByOrderByParentProduct_NameAsc();

    /** Offered to the breakdown-execution picker — see PackagingBreakdownComponent/PackagingService#breakdown. */
    List<PackagingRule> findByActiveTrueOrderByParentProduct_NameAsc();

    /** v1.68.0: the active rules that make this product out of a carton (the online shop counts and breaks those cartons down). */
    List<PackagingRule> findByChildProductIdAndActiveTrueOrderByIdAsc(Long childProductId);

    /** Used to reject a duplicate (parent, child) pair — see PackagingService#applyRequest. */
    Optional<PackagingRule> findByParentProductIdAndChildProductId(Long parentProductId, Long childProductId);
}
