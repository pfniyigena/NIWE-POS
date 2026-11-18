package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OnlineListingImageRepository extends JpaRepository<OnlineListingImage, Long> {
    /** Without the picture bytes: id, file name, size, sort order. */
    @Query("select i.id, i.fileName, i.sizeBytes, i.sortOrder from OnlineListingImage i where i.listingId = :listingId order by i.sortOrder, i.id")
    List<Object[]> overview(@Param("listingId") Long listingId);

    @Query("select i.id from OnlineListingImage i where i.listingId = :listingId order by i.sortOrder, i.id")
    List<Long> ids(@Param("listingId") Long listingId);

    long countByListingId(Long listingId);

    @Query("select coalesce(max(i.sortOrder), 0) from OnlineListingImage i where i.listingId = :listingId")
    int maxOrder(@Param("listingId") Long listingId);

    List<OnlineListingImage> findByListingIdOrderBySortOrderAscIdAsc(Long listingId);
}
