package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OnlineCartLineRepository extends JpaRepository<OnlineCartLine, Long> {
    List<OnlineCartLine> findByCartIdOrderByIdAsc(Long cartId);

    @Modifying
    @Query("delete from OnlineCartLine l where l.cartId = :id")
    int deleteForCart(@Param("id") Long cartId);

    @Query("select l.cartId, count(l), coalesce(sum(l.quantity), 0) from OnlineCartLine l where l.cartId in :ids group by l.cartId")
    List<Object[]> totals(@Param("ids") List<Long> cartIds);
}
