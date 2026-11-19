package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OnlineStockAlertRepository extends JpaRepository<OnlineStockAlert, Long> {

    List<OnlineStockAlert> findByStatusOrderByIdAsc(String status);

    List<OnlineStockAlert> findAllByOrderByIdDesc();

    Optional<OnlineStockAlert> findFirstByProductIdAndEmailAndStatus(Long productId, String email, String status);

    Optional<OnlineStockAlert> findFirstByProductIdAndPhoneAndStatus(Long productId, String phone, String status);

    long countByStatus(String status);

    /** product id, name, people waiting: the products customers are asking for, the most wanted first. */
    @Query("select a.productId, max(a.productName), count(a) from OnlineStockAlert a where a.status = 'WAITING' group by a.productId order by count(a) desc, a.productId asc")
    List<Object[]> demand();
}
