package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    List<StockCount> findAllByOrderByCreatedAtDesc();
}
