package com.posapp.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LoyaltyDtos {

    private LoyaltyDtos() {
    }

    public record Settings(boolean enabled, BigDecimal spendAmount, int pointsEarned, BigDecimal pointValue, int minRedeemPoints, BigDecimal maxRedeemPercent) {
    }

    public record CustomerRow(Long customerId, String name, String phone, int points, BigDecimal value, Instant lastActivity) {
    }

    public record TxnRow(Long id, String type, int points, int balanceAfter, Long orderId, String invoiceNumber, String note, Instant at) {
    }

    public record CustomerDetail(Long customerId, String name, int points, BigDecimal value, List<TxnRow> history) {
    }

    /** What the till shows for the customer on the cart. */
    public record Balance(Long customerId, boolean enabled, int points, BigDecimal value, BigDecimal pointValue, int minRedeemPoints, BigDecimal maxRedeemPercent) {
    }

    public record Summary(long customers, long outstandingPoints, BigDecimal outstandingValue) {
    }

    public record AdjustRequest(Integer points, String note) {
    }
}
