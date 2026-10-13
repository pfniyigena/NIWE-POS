package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One step of the progressive income tax: the part of taxable pay between {@link #fromAmount} and {@link #toAmount} is taxed at {@link #ratePercent}. */
@Getter
@Setter
@Entity
public class TaxBand extends BaseEntity {

    @Column(nullable = false)
    private BigDecimal fromAmount;

    /** Null = no upper limit. */
    private BigDecimal toAmount;

    @Column(nullable = false)
    private BigDecimal ratePercent;

    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int sortOrder = 0;
}
