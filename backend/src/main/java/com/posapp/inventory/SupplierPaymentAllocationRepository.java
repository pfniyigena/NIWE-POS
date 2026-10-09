package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierPaymentAllocationRepository extends JpaRepository<SupplierPaymentAllocation, Long> {

    /** Fourier: "same workflow as Sale order" — every allocation ever recorded against one bill, backing that bill's own payment history; see {@code SupplierPaymentService.paymentHistoryForBill}. */
    List<SupplierPaymentAllocation> findByBillId(Long billId);

    /**
     * How much of one purchase order's {@code totalCost} has already been paid off, across every
     * allocation ever recorded against it — the accounts-payable mirror of {@code
     * CustomerPaymentAllocationRepository.sumAppliedForOrder}, and the one query {@code
     * SupplierPaymentService.amountOwedForOrder} builds every "outstanding" figure in this app
     * from (the allocation-suggestion screen, the Supplier Statement and Outstanding Payables
     * reports). {@code coalesce(..., 0)} so a PO with no allocations yet returns zero rather than
     * null.
     */
    @Query("select coalesce(sum(a.amountApplied), 0) from SupplierPaymentAllocation a where a.purchaseOrder.id = :purchaseOrderId")
    BigDecimal sumAppliedForPurchaseOrder(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * The Bill mirror of {@link #sumAppliedForPurchaseOrder} — every new back-office supplier
     * payment's own target once a Bill exists, see {@code SupplierPaymentAllocation}'s "exclusive
     * arc" javadoc.
     */
    @Query("select coalesce(sum(a.amountApplied), 0) from SupplierPaymentAllocation a where a.bill.id = :billId")
    BigDecimal sumAppliedForBill(@Param("billId") Long billId);
}
