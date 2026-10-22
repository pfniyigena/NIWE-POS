package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface GuestDocumentRepository extends JpaRepository<GuestDocument, Long> {
    List<GuestDocument> findByGuestIdOrderByIdDesc(Long guestId);

    long countByGuestId(Long guestId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GuestDocument d set d.guestId = :to where d.guestId = :from")
    int moveGuest(@Param("from") Long from, @Param("to") Long to);
}
