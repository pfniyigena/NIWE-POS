package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPriceRepository extends JpaRepository<SupplierPrice, Long> {

    List<SupplierPrice> findAllByOrderBySupplierNameAscProductNameAsc();

    List<SupplierPrice> findBySupplierIdOrderByProductNameAsc(Long supplierId);

    boolean existsBySupplierId(Long supplierId);
}
