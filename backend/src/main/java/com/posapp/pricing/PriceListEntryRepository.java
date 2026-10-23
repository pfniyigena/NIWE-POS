package com.posapp.pricing;
import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListEntryRepository extends JpaRepository<PriceListEntry, Long> {
    List<PriceListEntry> findByPriceListId(Long priceListId);
    List<PriceListEntry> findByPriceListIdAndProductId(Long priceListId, Long productId);
    Optional<PriceListEntry> findByPriceListIdAndProductIdAndMinQuantity(Long priceListId, Long productId, BigDecimal minQuantity);
    long countByPriceListId(Long priceListId);
    boolean existsByProductId(Long productId);
}
