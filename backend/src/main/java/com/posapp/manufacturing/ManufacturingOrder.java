package com.posapp.manufacturing;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Manufacturing Module — "Manufacturing orders" (Fourier's own list, item 3). One order to
 * produce {@code quantityPlanned} units of {@code product} against one {@link Bom}'s recipe.
 * {@code routing} is copied from {@code bom.getRouting()} at {@link
 * ManufacturingOrderService#confirm} time — a snapshot, same "lock in what was true when this was
 * committed to" convention every other order type in this app already follows for its own
 * referenced records (a Sale Order line snapshots its product's price/tax; a Purchase Order line
 * snapshots its product's cost) — so a later edit to the Bom's own routing never rewrites an
 * order already in progress.
 *
 * <p>Only {@link ManufacturingOrderService#produce} ever touches {@code Product.stockQuantity} —
 * for both the components (decremented) and {@code product} itself (incremented) — via {@link
 * com.posapp.catalog.CatalogService#adjustStock}; creating or confirming an order never does,
 * exactly the same "committing to something isn't the same as it having happened" stance {@code
 * SalesOrderService}/{@code PurchaseOrderService} already take. Each call to {@code produce} is
 * one production run, recorded as its own {@link ManufacturingOrderRun} — accepts a partial
 * quantity and can be called more than once against the same order, mirroring {@code
 * SalesOrderService#fulfillLines}/{@code PurchaseOrderService#receiveLines} exactly.
 *
 * <p>{@code sourcePlanLineId} is purely informational — set when this order was generated from a
 * {@link ProductionPlanLine} (Fourier's "Production plans"), so the order's own detail screen can
 * say where it came from; nothing here reads it back.
 */
@Getter
@Setter
@Entity
public class ManufacturingOrder extends BaseEntity {

    /** Auto-assigned at creation, same "MO-" + millis shape as every other order number in this app. */
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    private Bom bom;

    /** Snapshotted from bom.getRouting() at confirm time — see class javadoc. Null if the Bom had none, or before this order is confirmed. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Routing routing;

    private int quantityPlanned;

    /** Cumulative across every run — see ManufacturingOrderRun. */
    private int quantityProduced = 0;

    @Enumerated(EnumType.STRING)
    private ManufacturingOrderStatus status = ManufacturingOrderStatus.DRAFT;

    /** Nullable — purely informational, nothing here auto-flags an overdue order, same stance as PurchaseOrder.expectedDate. */
    private LocalDate scheduledDate;

    /** Set once quantityProduced reaches quantityPlanned. */
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** See class javadoc — informational link back to the ProductionPlanLine this order was generated from, if any. */
    private Long sourcePlanLineId;


    /**
     * Fourier: multi-warehouse phase 4 — the location this order consumes components from and puts finished goods into (one location for both) moved stock at. Nullable: null
     * means the legacy behaviour (the product's plain primary-warehouse stock figure), which is
     * what every row created before this column existed did. {@code locationName}/{@code
     * warehouseName} are snapshots so renaming either later doesn't rewrite the trail.
     */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "location_id")
    private com.posapp.warehouse.WarehouseLocation location;

    private String locationName;

    private String warehouseName;

    @OneToMany(mappedBy = "manufacturingOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ManufacturingOrderOverhead> overheads = new ArrayList<>();

    @OneToMany(mappedBy = "manufacturingOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ManufacturingOrderRun> runs = new ArrayList<>();
}
