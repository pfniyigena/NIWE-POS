package com.posapp.onlinestore;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OnlineDeliverySlotRepository extends JpaRepository<OnlineDeliverySlot, Long> {
    List<OnlineDeliverySlot> findAllByOrderBySortOrderAscStartTimeAsc();

    /** Under a lock, so two customers cannot both take the last place of a window. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OnlineDeliverySlot s where s.id = :id")
    Optional<OnlineDeliverySlot> lockById(@Param("id") Long id);
}
