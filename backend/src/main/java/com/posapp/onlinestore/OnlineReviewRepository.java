package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OnlineReviewRepository extends JpaRepository<OnlineReview, Long> {
    List<OnlineReview> findByOnlineOrderId(Long onlineOrderId);

    Optional<OnlineReview> findByOnlineOrderIdAndProductId(Long onlineOrderId, Long productId);

    long countByStatus(String status);

    List<OnlineReview> findAllByOrderByIdDesc();

    List<OnlineReview> findByStatusOrderByIdDesc(String status);

    /** The newest approved reviews that have words in them, for the front page. */
    @Query("select r from OnlineReview r where r.status = 'APPROVED' and r.reviewText is not null and r.reviewText <> '' order by r.id desc")
    List<OnlineReview> findShown(org.springframework.data.domain.Pageable page);

    /** Per product: average rating and number of approved reviews. */
    @Query("select r.productId, avg(r.rating), count(r) from OnlineReview r where r.status = 'APPROVED' and r.productId in :ids group by r.productId")
    List<Object[]> ratings(@Param("ids") Collection<Long> ids);

    /** Over the whole shop: average rating and number of approved reviews. */
    @Query("select avg(r.rating), count(r) from OnlineReview r where r.status = 'APPROVED'")
    List<Object[]> overall();
}
