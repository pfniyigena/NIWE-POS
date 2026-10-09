package com.posapp.inventory;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a supplier charges for a product (net unit cost, before tax). Optionally limited to a date range;
 * when several prices for the same supplier and product are valid today the one with the latest start date
 * wins. Purchase orders prefill their unit cost from here.
 */
@Getter
@Setter
@Entity
public class SupplierPrice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    /** Null = valid from the beginning. */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** Null = no end date. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(columnDefinition = "TEXT")
    private String note;
}
