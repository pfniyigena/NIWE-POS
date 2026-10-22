package com.posapp.giftcards;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {
    Optional<GiftCard> findByCodeIgnoreCase(String code);

    /** The card, locked for the rest of the transaction so two sales cannot spend the same money. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GiftCard c where c.id = :id")
    Optional<GiftCard> lockById(Long id);

    List<GiftCard> findAllByOrderByIdDesc();

    @Query("select coalesce(sum(c.balance), 0) from GiftCard c")
    java.math.BigDecimal totalBalance();
}
