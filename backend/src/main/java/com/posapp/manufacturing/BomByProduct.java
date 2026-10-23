package com.posapp.manufacturing;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * MF-4: something else a recipe produces besides the finished good (bran from milling, trimmings, whey). Each good unit made
 * adds {@code quantityPerUnit} of it to stock. {@code costSharePercent} is the part of the run's cost that belongs to it
 * (0 = none); that part is taken off the main product's unit cost.
 */
@Getter
@Setter
@Entity
public class BomByProduct extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Bom bom;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private BigDecimal quantityPerUnit = BigDecimal.ZERO;

    private BigDecimal costSharePercent = BigDecimal.ZERO;
}
