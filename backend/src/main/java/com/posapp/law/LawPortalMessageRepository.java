package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawPortalMessageRepository extends JpaRepository<LawPortalMessage, Long> {
    List<LawPortalMessage> findByMatterIdOrderByIdAsc(Long matterId);

    long countByMatterIdAndFromClientTrueAndReadByStaffFalse(Long matterId);

    long countByMatterIdAndFromClientFalseAndReadByClientFalse(Long matterId);
}
