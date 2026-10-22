package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of an owner statement. {@code amount} is signed from the owner's side: rent is positive, the fee and repairs are negative. */
@Getter
@Setter
@Entity
@Table(name = "pm_statement_line", indexes = {@Index(columnList = "statement_id"), @Index(columnList = "kind, source_id")})
public class PmStatementLine extends BaseEntity {

    @Column(nullable = false)
    private Long statementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatementLineKind kind;

    private Long propertyId;

    private Long unitId;

    private Long tenantId;

    @Column(nullable = false)
    private LocalDate lineDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 60)
    private String reference;

    /** For a fee line: the rent the fee was worked out on. */
    @Column(precision = 18, scale = 2)
    private BigDecimal baseAmount;

    /** For a fee line: the percentage used. */
    @Column(precision = 7, scale = 2)
    private BigDecimal feePercent;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** The payment allocation (rent) or maintenance request (repair) this line came from, so nothing is counted twice. */
    private Long sourceId;
}
