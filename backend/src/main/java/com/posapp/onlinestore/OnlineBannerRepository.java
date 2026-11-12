package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OnlineBannerRepository extends JpaRepository<OnlineBanner, Long> {
    /** Without the picture bytes: id, file name, size, sort order. */
    @Query("select b.id, b.fileName, b.sizeBytes, b.sortOrder from OnlineBanner b order by b.sortOrder, b.id")
    List<Object[]> overview();

    @Query("select b.id from OnlineBanner b order by b.sortOrder, b.id")
    List<Long> ids();

    @Query("select coalesce(max(b.sortOrder), 0) from OnlineBanner b")
    int maxOrder();
}
