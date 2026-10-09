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
 * One component actually consumed in one {@link ManufacturingOrderRun} — the per-component
 * counterpart to {@code SalesOrderReturnLine}/{@code PurchaseReturnLine}'s own per-line
 * {@code stockBefore}/{@code stockAfter} snapshots, which is what lets {@code
 * ReportService.stockMovementHistory} show one dated "Manufacturing consumption" row per component
 * per run. {@code componentName}/{@code unitCost} are snapshotted at consumption time, same
 * "don't rewrite history when the product later changes" convention as every other trail entity
 * here (StockReceipt.productName, PurchaseOrderLine.unitCost, ...). {@code stockBefore}/{@code
 * stockAfter} are null when the component isn't stock-tracked ({@link
 * Product#isTrackStock()} false) — see {@code ManufacturingOrderService#produce} for why nothing
 * is deducted for one, mirroring how a non-tracked line already produces no snapshot anywhere else
 * in this app.
 */
@Getter
@Setter
@Entity
public class ManufacturingOrderConsumption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingOrderRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product component;

    private String componentName;

    private int quantityConsumed;

    /** Snapshot of component.costPrice at the moment of this run — see class javadoc. */
    private BigDecimal unitCost = BigDecimal.ZERO;

    private Integer stockBefore;

    private Integer stockAfter;
}
