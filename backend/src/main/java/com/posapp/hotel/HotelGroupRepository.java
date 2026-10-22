package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HotelGroupRepository extends JpaRepository<HotelGroup, Long>, JpaSpecificationExecutor<HotelGroup> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update HotelGroup g set g.contactGuest = :to where g.contactGuest.id = :from")
    int moveContact(@Param("from") Long from, @Param("to") Guest to);
}
