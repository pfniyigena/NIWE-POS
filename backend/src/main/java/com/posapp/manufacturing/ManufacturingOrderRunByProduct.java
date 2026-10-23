package com.posapp.manufacturing;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** MF-4: a by-product that came out of one production run, with the part of the run's cost it took. */
@Getter
@Setter
@Entity
public class ManufacturingOrderRunByProduct extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingOrderRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private String productName;

    private BigDecimal quantity = BigDecimal.ZERO;

    private BigDecimal costValue = BigDecimal.ZERO;

    private BigDecimal stockBefore;

    private BigDecimal stockAfter;
}
