package com.posapp.manufacturing;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * One product's demand line on a {@link ProductionPlan}. {@code demandFromSales} is the sum of
 * {@code quantityOrdered - quantityFulfilled} across every CONFIRMED/PARTIALLY_FULFILLED {@code
 * SalesOrderLine} for this product at generation time. {@code demandFromLowStock} is how much
 * additional quantity would be needed to bring this product's stock up to the plan's own {@code
 * lowStockThreshold}, using whatever stock is left over after covering {@code demandFromSales}
 * first — see {@code ProductionPlanService.generate}'s javadoc for the exact formula.
 * {@code suggestedQuantity} is simply their sum, computed once at generation time; an admin can
 * still produce a different quantity than suggested when actually generating a Manufacturing
 * Order from this line (see {@link #getGeneratedManufacturingOrder()}).
 *
 * <p>{@code bom} is this product's active Bom at generation time, or null if it has none (a
 * product can still show up here from sales/low-stock demand even without a recipe yet — the plan
 * surfaces that gap rather than silently skipping the product — but a line with no Bom can't be
 * converted into a Manufacturing Order until one is added).
 */
@Getter
@Setter
@Entity
public class ProductionPlanLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at generation time, same "survives a later rename" convention as every other trail entity here. */
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    private Bom bom;

    private int demandFromSales;

    private int demandFromLowStock;

    private int suggestedQuantity;

    /** Set once this line has been converted into a real order (see ProductionPlanService.generateManufacturingOrder) — prevents generating a duplicate and lets the plan screen show what happened. */
    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingOrder generatedManufacturingOrder;
}
