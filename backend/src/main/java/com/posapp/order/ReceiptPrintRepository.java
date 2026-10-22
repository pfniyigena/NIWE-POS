package com.posapp.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptPrintRepository extends JpaRepository<ReceiptPrint, Long> {
    List<ReceiptPrint> findByCopyNumberGreaterThanOrderByCreatedAtDesc(int copyNumber);
}
