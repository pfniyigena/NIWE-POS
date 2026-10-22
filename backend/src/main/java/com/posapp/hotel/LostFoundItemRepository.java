package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface LostFoundItemRepository extends JpaRepository<LostFoundItem, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LostFoundItem i set i.guest = :to where i.guest.id = :from")
    int moveGuest(@Param("from") Long from, @Param("to") Guest to);
}
