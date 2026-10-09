package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

    List<PurchaseReturn> findAllByOrderByReturnDateDesc();

    List<PurchaseReturn> findByPurchaseOrderIdOrderByReturnDateDesc(Long purchaseOrderId);

    /** Task 6: "Stock Movement History" — powers ReportService.stockMovementHistory. */
    List<PurchaseReturn> findByReturnDateBetweenOrderByReturnDateDesc(Instant from, Instant to);

    /**
     * How much has ever been returned against one Purchase Order, across every return visit — the
     * one figure {@code SupplierPaymentService.amountOwedForOrder}/{@code
     * refundDueFromSupplierForOrder} both build on, same "one query everything reuses" shape as
     * {@code SupplierPaymentAllocationRepository.sumAppliedForPurchaseOrder}.
     */
    @Query("select coalesce(sum(r.refundTotal), 0) from PurchaseReturn r where r.purchaseOrder.id = :purchaseOrderId")
    BigDecimal sumRefundTotalForPurchaseOrder(@Param("purchaseOrderId") Long purchaseOrderId);
}
