package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BomRepository extends JpaRepository<Bom, Long> {

    List<Bom> findAllByOrderByProduct_NameAsc();

    /** Every manufactured product's default recipe, for ProductionPlanService.generate to aggregate demand against. */
    List<Bom> findByActiveTrueOrderByProduct_NameAsc();

    List<Bom> findByProductIdOrderByActiveDesc(Long productId);

    Optional<Bom> findFirstByProductIdAndActiveTrue(Long productId);

    /** Whether a product is manufactured at all (has any Bom, active or not) — CatalogService/frontend use this to badge a product as a finished good rather than adding a redundant flag on Product itself. */
    boolean existsByProductId(Long productId);
}
