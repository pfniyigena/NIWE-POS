package com.posapp.giftcards;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class GiftCardDtos {

    private GiftCardDtos() {
    }

    public record Row(Long id, String code, BigDecimal initialAmount, BigDecimal balance, String status, Long customerId, String customerName, String recipientName,
                      LocalDate expiresOn, boolean expired, boolean usable, String note, Instant createdAt) {
    }

    public record TxnRow(Long id, String type, BigDecimal amount, BigDecimal balanceAfter, String paymentMethod, Long orderId, String invoiceNumber, String note, Instant at) {
    }

    public record Detail(Row card, List<TxnRow> history) {
    }

    public record Summary(BigDecimal liability, long cards, long withBalance, long blocked, long expiredWithBalance, BigDecimal expiredBalance) {
    }

    public record Released(long cards, BigDecimal amount) {
    }

    public record SellRequest(BigDecimal amount, String paymentMethod, Long customerId, String recipientName, LocalDate expiresOn, String code, String note, Long cashierSessionId) {
    }

    public record TopUpRequest(BigDecimal amount, String paymentMethod, Long cashierSessionId, String note) {
    }

    public record ExpiryRequest(LocalDate expiresOn) {
    }
}
