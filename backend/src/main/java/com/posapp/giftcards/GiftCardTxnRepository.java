package com.posapp.giftcards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface GiftCardTxnRepository extends JpaRepository<GiftCardTxn, Long> {
    List<GiftCardTxn> findByGiftCardIdOrderByIdDesc(Long giftCardId);

    /** Cards sold or topped up in a shift (the money that went into the drawer or the bank at that till). */
    @Query("select t from GiftCardTxn t where t.registerSessionId = :sessionId and t.type in (com.posapp.giftcards.GiftCardTxnType.SOLD, com.posapp.giftcards.GiftCardTxnType.TOPUP)")
    List<GiftCardTxn> soldInRegisterSession(Long sessionId);

    @Query("select t from GiftCardTxn t where t.cashierSessionId = :cashierSessionId and t.type in (com.posapp.giftcards.GiftCardTxnType.SOLD, com.posapp.giftcards.GiftCardTxnType.TOPUP)")
    List<GiftCardTxn> soldInCashierSession(Long cashierSessionId);

    /** How much has already been given back to this card for this sale. */
    @Query("select coalesce(sum(t.amount), 0) from GiftCardTxn t where t.orderId = :orderId and t.giftCardId = :cardId and t.type = com.posapp.giftcards.GiftCardTxnType.REFUND")
    BigDecimal refundedForOrder(Long orderId, Long cardId);
}
