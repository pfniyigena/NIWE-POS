package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LawPortalAccountRepository extends JpaRepository<LawPortalAccount, Long> {
    Optional<LawPortalAccount> findByEmail(String email);

    Optional<LawPortalAccount> findByInviteTokenHash(String hash);

    List<LawPortalAccount> findByCustomerIdOrderByIdAsc(Long customerId);
}
