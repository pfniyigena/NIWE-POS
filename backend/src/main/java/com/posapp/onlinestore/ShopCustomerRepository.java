package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopCustomerRepository extends JpaRepository<ShopCustomer, Long> {
    Optional<ShopCustomer> findByEmail(String email);

    List<ShopCustomer> findByPhone(String phone);

    Optional<ShopCustomer> findByPhoneKeyAndPhoneVerifiedTrue(String phoneKey);

    /** Spending or changing points locks the account, so the same points cannot be used twice at once. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShopCustomer c where c.id = :id")
    Optional<ShopCustomer> lockById(@Param("id") Long id);

    @Query("select c from ShopCustomer c where :q = '' or lower(c.email) like :q or lower(c.name) like :q or c.phone like :q order by c.loyaltyPoints desc, c.id")
    List<ShopCustomer> search(@Param("q") String q, org.springframework.data.domain.Pageable page);

    long countByLoyaltyPointsGreaterThan(int points);

    @Query("select coalesce(sum(c.loyaltyPoints), 0) from ShopCustomer c where c.loyaltyPoints > 0")
    long sumPoints();
}
