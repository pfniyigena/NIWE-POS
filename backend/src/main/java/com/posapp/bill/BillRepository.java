package com.posapp.bill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findAllByOrderByBillDateDesc();

    /**
     * The one bill (of any status — VOID included) for this Purchase Order, if one has ever been
     * generated — the accounts-payable mirror of {@code InvoiceRepository.findBySalesOrderId}.
     * Exactly one bill document is ever created per Purchase Order in this app (see {@code Bill}'s
     * class javadoc); {@code BillService.generate} uses this to refuse a second attempt, and the
     * Purchase Order detail screen uses it to show "View bill" instead of "Generate bill" once one
     * exists. Also what {@code SupplierPaymentService.amountOwedForOrder}/{@code
     * amountPaidForOrder} use to redirect a billed order's own figures through its Bill once one
     * exists.
     */
    Optional<Bill> findByPurchaseOrderId(Long purchaseOrderId);

    /** Every bill ever generated against this supplier's purchase orders, oldest first — backs {@code SupplierPaymentService.listOutstandingOrders}' new Bill-targeted rows. */
    List<Bill> findBySupplierIdOrderByBillDateAsc(Long supplierId);

    /** Every voided bill, store-wide, newest-voided first — the accounts-payable mirror of {@code InvoiceRepository.findByStatusOrderByVoidedAtDesc}; backs {@code BillService.listRefunds}. */
    List<Bill> findByStatusOrderByVoidedAtDesc(BillStatus status);
}
