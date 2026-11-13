package com.posapp.onlinestore;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OnlineCartRepository extends JpaRepository<OnlineCart, Long> {
    Optional<OnlineCart> findByEmail(String email);

    Optional<OnlineCart> findByToken(String token);

    /** Carts whose next reminder is due: the first one 1 hour after the last change, the second one 3 days after it. */
    @Query("select c from OnlineCart c where ((c.status = 'ACTIVE' and c.reminderCount = 0 and c.savedAt <= :firstDue) "
            + "or (c.status = 'REMINDED' and c.reminderCount = 1 and c.savedAt <= :secondDue)) "
            + "and (c.nextTryAt is null or c.nextTryAt <= :now) order by c.savedAt asc")
    List<OnlineCart> due(@Param("firstDue") Instant firstDue, @Param("secondDue") Instant secondDue, @Param("now") Instant now, Pageable page);

    @Query("select c.status, count(c) from OnlineCart c group by c.status")
    List<Object[]> counts();

    List<OnlineCart> findAllByOrderBySavedAtDesc(Pageable page);

    List<OnlineCart> findByStatusOrderBySavedAtDesc(String status, Pageable page);

    @Query("select c.id from OnlineCart c where c.updatedAt < :before and c.status <> 'STOPPED'")
    List<Long> idsNotTouchedSince(@Param("before") Instant before);

    @Modifying
    @Query("delete from OnlineCart c where c.id in :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
