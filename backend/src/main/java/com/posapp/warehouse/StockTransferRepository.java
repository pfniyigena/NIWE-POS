package com.posapp.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    List<StockTransfer> findAllByOrderByCreatedAtDesc();

    /** Transfers that moved (or are moving) stock — Stock Movement History reads these. */
    List<StockTransfer> findByStatusIn(java.util.Collection<StockTransferStatus> statuses);

    /** {@code [productId, quantity]} for stock currently on the road between branches. */
    @org.springframework.data.jpa.repository.Query("select t.product.id, sum(t.quantity) from StockTransfer t " +
            "where t.status = com.posapp.warehouse.StockTransferStatus.IN_TRANSIT and t.product is not null group by t.product.id")
    List<Object[]> inTransitByProduct();
}
