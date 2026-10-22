package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReViewingRepository extends JpaRepository<ReViewing, Long> {
    List<ReViewing> findByLeadIdOrderByStartAtDesc(Long leadId);

    List<ReViewing> findByPropertyIdOrderByStartAtDesc(Long propertyId);

    List<ReViewing> findByStartAtBetweenOrderByStartAtAsc(Instant from, Instant to);

    List<ReViewing> findByAgentUserIdAndStatus(Long agentUserId, ViewingStatus status);

    List<ReViewing> findByStatusAndStartAtBetweenOrderByStartAtAsc(ViewingStatus status, Instant from, Instant to);

    long countByPropertyId(Long propertyId);

    long countByLeadId(Long leadId);
}
