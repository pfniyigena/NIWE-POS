package com.posapp.promotions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PromotionDtos {

    private PromotionDtos() {
    }

    public record Row(Long id, String name, String description, String type, String scope, Long targetId, String targetName, BigDecimal value, Integer buyQuantity, Integer freeQuantity,
                      int minQuantity, BigDecimal minSpend, LocalDate validFrom, LocalDate validTo, String couponCode, Integer maxUses, Integer maxUsesPerCustomer,
                      Long customerGroupId, String customerGroupName, boolean active, boolean current, long uses, BigDecimal given, String summary) {
    }

    public record Request(String name, String description, String type, String scope, Long targetId, BigDecimal value, Integer buyQuantity, Integer freeQuantity, Integer minQuantity,
                          BigDecimal minSpend, LocalDate validFrom, LocalDate validTo, String couponCode, Integer maxUses, Integer maxUsesPerCustomer, Long customerGroupId, Boolean active) {
    }

    public record UseRow(Long orderId, String invoiceNumber, String customerName, BigDecimal amount, Instant at) {
    }

    /** What a cart got from promotions: the promotion, the coupon behind it (if any) and how much it took off. */
    public record Applied(Long promotionId, String name, String couponCode, BigDecimal amount) {
    }
}
