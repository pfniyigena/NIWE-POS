package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolioPaymentRepository extends JpaRepository<FolioPayment, Long> {
    List<FolioPayment> findByBookingIdOrderByIdAsc(Long bookingId);
}
