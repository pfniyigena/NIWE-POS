package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawMatterPartyRepository extends JpaRepository<LawMatterParty, Long> {
    List<LawMatterParty> findByMatterIdOrderByIdAsc(Long matterId);

    List<LawMatterParty> findByContactId(Long contactId);

    long countByContactId(Long contactId);

    void deleteByMatterId(Long matterId);
}
