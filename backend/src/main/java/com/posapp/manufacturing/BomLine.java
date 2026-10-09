package com.posapp.manufacturing;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * One component line on a {@link Bom}. {@code quantityPerUnit} is deliberately "how many of this
 * component to make ONE unit of the finished good" rather than a batch-yield ratio (e.g. "this BOM
 * makes 10 units from these quantities") — every quantity field in this app (OrderLine, PurchaseOrderLine,
 * SalesOrderLine, StockAdjustment, ...) is a plain whole-number {@code int}, never a fraction, so a
 * batch-yield model would need fractional component quantities (or rounding) the moment a
 * Manufacturing Order produces a quantity that doesn't divide evenly into the batch size. Per-unit
 * quantities sidestep that entirely: the components needed for N finished units is always exactly
 * {@code quantityPerUnit * N}, no rounding, no remainder — see {@code
 * BomService#requiredComponentQuantities}.
 */
@Getter
@Setter
@Entity
public class BomLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Bom bom;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product component;

    private int quantityPerUnit;
}
