package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findAllByOrderByOrderDateDesc();

    /** Open orders still expecting goods — the Reorder report counts their outstanding quantity as "on order". */
    List<PurchaseOrder> findByStatusIn(java.util.Collection<PurchaseOrderStatus> statuses);

    /** Guards SupplierService.delete — a supplier with purchase order history is deactivated rather than removed. See that method's javadoc. */
    boolean existsBySupplierId(Long supplierId);

    /** Task 4: "Add Purchase order reports" — see ReportService.purchaseOrders. */
    List<PurchaseOrder> findByOrderDateBetweenOrderByOrderDateDesc(Instant from, Instant to);

    /** Task 5: "Add Supplier Statement" — every PO ever placed with one supplier, oldest first (same "always chronological, no date range" shape as OrderRepository.findCompletedForCustomerStatement). */
    List<PurchaseOrder> findBySupplierIdAndOrderDateBetweenOrderByOrderDateAsc(Long supplierId, Instant from, Instant to);
}
