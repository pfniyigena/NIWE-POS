package com.posapp.tracking;

import com.posapp.catalog.Product;
import com.posapp.warehouse.WarehouseLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockSerialRepository extends JpaRepository<StockSerial, Long> {

    Optional<StockSerial> findByProductAndSerialNumber(Product product, String serialNumber);

    List<StockSerial> findByProductAndLocationAndStatusOrderByIdAsc(Product product, WarehouseLocation location, SerialStatus status);

    List<StockSerial> findByTransferIdAndProductOrderByIdAsc(Long transferId, Product product);

    @Query("select s from StockSerial s join fetch s.product left join fetch s.location l left join fetch l.warehouse order by s.id desc")
    List<StockSerial> findAllWithDetails();

    @Query("select s from StockSerial s left join fetch s.location l left join fetch l.warehouse where s.product.id = :productId order by s.id desc")
    List<StockSerial> findByProductIdWithDetails(@Param("productId") Long productId);

    void deleteByProductId(Long productId);
}
