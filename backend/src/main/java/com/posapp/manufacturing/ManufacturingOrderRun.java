package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One production run against a {@link ManufacturingOrder} — the fulfillment-side counterpart to
 * {@code SalesOrderFulfillment}/{@code StockReceipt}: {@code
 * ManufacturingOrder.quantityProduced} is only a cumulative running total with no per-event
 * timestamp, so this is what lets {@code ReportService.stockMovementHistory} show "how much was
 * produced, and when" as a dated row, and what lets the order's own detail screen show a
 * run-by-run cost/production history rather than just one final total.
 *
 * <p>{@code materialCost}/{@code overheadCost}/{@code totalCost} are this run's own share, computed
 * once at production time from that moment's component cost prices and the order's snapshotted
 * overhead pools (see {@code ManufacturingOrderService#produce}) — never recomputed later, same
 * "snapshot at event time" convention as every other cost figure in this app. {@code
 * stockBefore}/{@code stockAfter} are the finished-good product's own stock immediately
 * before/after this run's output was added — null when the product isn't stock-tracked ({@link
 * com.posapp.catalog.Product#isTrackStock()} false), same "no snapshot, no row" treatment used
 * throughout {@code ReportService.stockMovementHistory}.
 */
@Getter
@Setter
@Entity
public class ManufacturingOrderRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingOrder manufacturingOrder;

    private int quantityProduced;

    private BigDecimal materialCost = BigDecimal.ZERO;

    private BigDecimal overheadCost = BigDecimal.ZERO;

    private BigDecimal totalCost = BigDecimal.ZERO;

    private Integer stockBefore;

    private Integer stockAfter;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ManufacturingOrderConsumption> consumptions = new ArrayList<>();
}
