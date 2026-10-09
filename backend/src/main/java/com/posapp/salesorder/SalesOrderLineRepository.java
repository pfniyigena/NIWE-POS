package com.posapp.salesorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    /** Quantity of one product still to be delivered on orders in the given statuses. */
    @Query("select coalesce(sum(l.quantityOrdered - l.quantityFulfilled), 0) from SalesOrderLine l " +
           "where l.product.id = :productId and l.salesOrder.status in :statuses")
    long outstandingQuantity(@Param("productId") Long productId, @Param("statuses") Collection<SalesOrderStatus> statuses);

    /** {@code [productId, outstanding quantity]} for every product with something still to deliver. */
    @Query("select l.product.id, sum(l.quantityOrdered - l.quantityFulfilled) from SalesOrderLine l " +
           "where l.product is not null and l.salesOrder.status in :statuses group by l.product.id")
    List<Object[]> outstandingByProduct(@Param("statuses") Collection<SalesOrderStatus> statuses);
}
