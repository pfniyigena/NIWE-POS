package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PmStatementLineRepository extends JpaRepository<PmStatementLine, Long> {
    List<PmStatementLine> findByStatementId(Long statementId);

    List<PmStatementLine> findByStatementIdIn(Collection<Long> statementIds);

    void deleteByStatementId(Long statementId);

    void deleteByStatementIdAndKindIn(Long statementId, Collection<StatementLineKind> kinds);

    /** The sources of lines of this kind that are already on a statement of this owner other than {@code exclude}. A rent payment shared by co-owners is on one statement of each. */
    @Query("select l.sourceId from PmStatementLine l where l.kind = :kind and l.sourceId is not null and l.statementId <> :exclude "
            + "and l.statementId in (select s.id from PmOwnerStatement s where s.ownerId = :ownerId)")
    List<Long> sourceIdsElsewhere(@Param("kind") StatementLineKind kind, @Param("ownerId") Long ownerId, @Param("exclude") Long exclude);

    /** The last day covered by an agreed (not draft) statement that has a line for the property, or null. */
    @Query("select max(st.periodTo) from PmOwnerStatement st where st.status <> :draft and st.id in (select l.statementId from PmStatementLine l where l.propertyId = :propertyId)")
    java.time.LocalDate lastAgreedDay(@Param("propertyId") Long propertyId, @Param("draft") StatementStatus draft);

    /** Draft statements with a line for the property dated on or after {@code from}. */
    @Query("select distinct l.statementId from PmStatementLine l where l.propertyId = :propertyId and l.lineDate >= :from and l.statementId in (select st.id from PmOwnerStatement st where st.status = :draft)")
    List<Long> draftStatementIds(@Param("propertyId") Long propertyId, @Param("from") java.time.LocalDate from, @Param("draft") StatementStatus draft);
}
