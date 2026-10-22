package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PmOwnerStatementRepository extends JpaRepository<PmOwnerStatement, Long> {
    boolean existsByOwnerId(Long ownerId);

    /** Statements of the owner that overlap the period [from, to]. */
    List<PmOwnerStatement> findByOwnerIdAndPeriodFromLessThanEqualAndPeriodToGreaterThanEqual(Long ownerId, LocalDate to, LocalDate from);

    List<PmOwnerStatement> findByStatus(StatementStatus status);
}
