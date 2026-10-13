package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One month's depreciation charge on one asset. */
@Getter
@Setter
@Entity
public class DepreciationEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private FixedAsset asset;

    /** The last day of the month this charge is for. */
    @Column(nullable = false)
    private LocalDate periodEnd;

    /** The branch this month's charge belongs to (the asset's branch at the time; null = head office). */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal accumulatedAfter;

    @Column(nullable = false)
    private BigDecimal bookValueAfter;
}
