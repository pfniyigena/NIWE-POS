package com.posapp.tracking;

import com.posapp.catalog.Product;
import com.posapp.warehouse.WarehouseLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    List<StockBatch> findByProductAndLocationAndQuantityGreaterThan(Product product, WarehouseLocation location, int quantity);

    List<StockBatch> findByTransferIdAndProduct(Long transferId, Product product);

    @Query("select b from StockBatch b join fetch b.product p left join fetch b.location l left join fetch l.warehouse where b.quantity > 0 order by p.name asc, b.expiryDate asc")
    List<StockBatch> findAllLive();

    @Query("select b from StockBatch b left join fetch b.location l left join fetch l.warehouse where b.product.id = :productId and b.quantity > 0 order by b.expiryDate asc, b.id asc")
    List<StockBatch> findLiveByProductId(@Param("productId") Long productId);

    void deleteByProductId(Long productId);
}
