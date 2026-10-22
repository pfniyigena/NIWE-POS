package com.posapp.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PricingDtos {

    private PricingDtos() {
    }

    public record ListDto(Long id, String name, String description, BigDecimal adjustPercent, LocalDate validFrom, LocalDate validTo, boolean active, boolean current, long entries, long groups) {
    }

    public record ListRequest(String name, String description, BigDecimal adjustPercent, LocalDate validFrom, LocalDate validTo, Boolean active) {
    }

    public record EntryDto(Long id, Long productId, String productName, String sku, BigDecimal basePrice, int minQuantity, BigDecimal price) {
    }

    public record EntryRequest(Long productId, Integer minQuantity, BigDecimal price) {
    }

    public record GroupDto(Long id, String name, String description, Long priceListId, String priceListName, boolean active, long customers) {
    }

    public record GroupRequest(String name, String description, Long priceListId, Boolean active) {
    }

    public record AssignRequest(java.util.List<Long> customerIds, Long groupId) {
    }

    /** What a customer pays for a product at a quantity. {@code note} names the price list when the price differs from the normal one. */
    public record Resolved(BigDecimal listPrice, BigDecimal unitPrice, String note) {
    }
}
