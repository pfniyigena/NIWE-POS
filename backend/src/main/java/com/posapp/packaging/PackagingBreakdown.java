package com.posapp.packaging;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One executed breakdown against a {@link PackagingRule} — the "run" counterpart to {@code
 * ManufacturingOrderRun}, giving {@code ReportService.stockMovementHistory} (and this module's own
 * detail screen) a dated row for "how much was broken down, and when". {@code rule} is kept as a
 * real reference (rules are "toggle, don't delete" — see {@code PackagingRule.active}'s javadoc —
 * so the FK never dangles), but {@code parentProductName}/{@code childProductName}/{@code
 * unitsPerParent} are snapshotted at breakdown time, same "don't rewrite history when the rule is
 * later edited" convention as {@code ManufacturingOrderConsumption.componentName}/{@code
 * BomLine.quantityPerUnit} — if the rule's ratio changes next week, last week's breakdown still
 * reads the way it actually happened.
 *
 * <p>{@code parentStockBefore}/{@code parentStockAfter}/{@code childStockBefore}/{@code
 * childStockAfter} are nullable {@code Integer}, not primitive {@code int} — null on whichever
 * side wasn't stock-tracked at breakdown time ({@link Product#isTrackStock()} false), same
 * "no snapshot, no row" treatment {@code ManufacturingOrderRun.stockBefore}/{@code stockAfter}
 * already use.
 *
 * <p>Deliberately carries no accounting-posting fields at all (contrast {@code
 * ManufacturingOrderRun.materialCost}/{@code overheadCost}/{@code totalCost}) — see {@link
 * PackagingService#breakdown}'s javadoc for why a breakdown posts nothing to accounting. {@link
 * #childUnitCost} below is a catalog-costing field, not an accounting one — it's the figure
 * {@code CatalogService#updateCostPrice} actually writes onto {@code childProduct.costPrice},
 * snapshotted here too so this row still shows what was applied even if the rule or either
 * product's cost later changes.
 */
@Getter
@Setter
@Entity
public class PackagingBreakdown extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private PackagingRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product parentProduct;

    private String parentProductName;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product childProduct;

    private String childProductName;

    /** Snapshot of rule.unitsPerParent at breakdown time — see class javadoc. */
    private int unitsPerParent;

    /** How many whole units of parentProduct were broken down in this run. */
    private int parentQuantity;

    /** parentQuantity * unitsPerParent, computed once at breakdown time. */
    private int childQuantityProduced;

    private Integer parentStockBefore;

    private Integer parentStockAfter;

    private Integer childStockBefore;

    private Integer childStockAfter;

    /**
     * Fourier: "When Breakdown product the cost of the child should be equal to the cost of the
     * parent divided by unit" — {@code parentProduct.costPrice / unitsPerParent}, rounded the
     * same way every other money figure in this app is (2dp, half-up), computed and written onto
     * {@code childProduct.costPrice} (via {@code CatalogService#updateCostPrice}) at the moment
     * this breakdown runs. Snapshotted here, same "don't rewrite history" reasoning as every
     * other field on this entity — see class javadoc.
     */
    private BigDecimal childUnitCost;

    @Column(columnDefinition = "TEXT")
    private String note;

    /**
     * Fourier: multi-warehouse phase 4 — the location the parent was taken from and the child put
     * into. Nullable: null means the legacy behaviour (the product's plain primary-warehouse stock
     * figure). {@code locationName}/{@code warehouseName} are snapshots.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "location_id")
    private com.posapp.warehouse.WarehouseLocation location;

    private String locationName;

    private String warehouseName;
}
