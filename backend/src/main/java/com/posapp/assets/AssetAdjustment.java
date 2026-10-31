package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A change to an asset's value or depreciation terms after it was registered: an improvement, an impairment or new terms. */
@Getter
@Setter
@Entity
public class AssetAdjustment extends BaseEntity {
    public static final String IMPROVEMENT = "IMPROVEMENT";
    public static final String IMPAIRMENT = "IMPAIRMENT";
    public static final String TERMS = "TERMS";

    @Column(name = "asset_id", nullable = false)
    private Long assetId;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(nullable = false)
    private LocalDate adjustmentDate;
    /** Improvement cost, impairment loss, or zero for a change of terms. */
    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;
    private String funding;
    private Integer remainingLifeMonths;
    private BigDecimal salvageValue;
    private String method;
    @Column(nullable = false)
    private BigDecimal costAfter;
    @Column(nullable = false)
    private BigDecimal bookValueAfter;
    @Column(length = 1000)
    private String note;
    private String createdByName;
}
