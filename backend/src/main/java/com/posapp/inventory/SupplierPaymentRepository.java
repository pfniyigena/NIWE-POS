package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    /** Task: "Add Supplier payment" — one supplier's own payment history, most recent first. */
    List<SupplierPayment> findBySupplierIdOrderByPaidAtDesc(Long supplierId);

    /** Store-wide payment list (mirrors the new store-wide Customer Payments screen). */
    List<SupplierPayment> findAllByOrderByPaidAtDesc();
}
