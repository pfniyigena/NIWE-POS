package com.posapp.onlinestore;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OnlineListingRepository extends JpaRepository<OnlineListing, Long> {
    List<OnlineListing> findAllByOrderBySortOrderAscIdAsc();

    List<OnlineListing> findByListedTrueOrderBySortOrderAscIdAsc();

    Optional<OnlineListing> findByProductId(Long productId);

    /** One page of what the public shop shows. q is a lower-case fragment (or empty) and category a category name (or empty): both are matched in the database. */
    @Query("select l from OnlineListing l join l.product p left join p.category c where l.listed = true and p.active = true "
            + "and (:category = '' or c.name = :category) "
            + "and (:q = '' or lower(p.name) like concat('%', :q, '%') or lower(coalesce(l.webTitle, '')) like concat('%', :q, '%')) "
            + "order by l.sortOrder asc, l.id asc")
    Page<OnlineListing> findShopPage(@Param("q") String q, @Param("category") String category, Pageable pageable);

    @Query("select distinct c.name from OnlineListing l join l.product p join p.category c where l.listed = true and p.active = true order by c.name")
    List<String> findShopCategories();
}
