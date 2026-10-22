package com.posapp.realestate;

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

/** A valuation of a property by a valuer: the value they put on it, on a date, for a purpose. The report is attached as a document. */
@Getter
@Setter
@Entity
@Table(name = "re_valuation", indexes = @Index(columnList = "property_id"))
public class ReValuation extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private LocalDate valuationDate;

    @Column(nullable = false, length = 200)
    private String valuerName;

    @Column(length = 200)
    private String valuerFirm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValuationPurpose purpose = ValuationPurpose.SALE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValuationMethod method = ValuationMethod.COMPARABLE;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal marketValue;

    @Column(precision = 18, scale = 2)
    private BigDecimal forcedSaleValue;

    @Column(precision = 18, scale = 2)
    private BigDecimal monthlyRent;

    private LocalDate validUntil;

    @Column(length = 2000)
    private String notes;

    private String createdByName;
}
