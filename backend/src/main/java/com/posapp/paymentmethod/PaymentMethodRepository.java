package com.posapp.paymentmethod;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, Long> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<PaymentMethodEntity> findByCodeIgnoreCase(String code);

    Optional<PaymentMethodEntity> findByCodeIgnoreCaseAndActiveTrue(String code);

    /** Task: "Make payment method order dynamic" — sortOrder first, name as the tiebreaker. */
    List<PaymentMethodEntity> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<PaymentMethodEntity> findAllByOrderBySortOrderAscNameAsc();

    /** Used by OrderService/ReturnsService to know which method codes count as cash-in-drawer. */
    List<PaymentMethodEntity> findByCashTrue();

    /** Used by OrderService to know which method codes represent selling on store credit. */
    List<PaymentMethodEntity> findByCreditTrue();
}
