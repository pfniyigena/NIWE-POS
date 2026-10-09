package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manufacturing Module — "Production plans" (Fourier's own list, item 2; scoped via
 * AskUserQuestion to "full MRP-style planning": aggregate demand across confirmed Sale Orders and
 * low-stock finished goods, surface material shortfalls, but — deliberately, to keep this a
 * bounded first pass — never auto-generate Purchase Orders; single-level BOM explosion only, no
 * recursive sub-assembly planning).
 *
 * <p>A run of {@link ProductionPlanService#generate} produces one of these as a saved snapshot —
 * not a live, always-recomputing view — so an admin can review, adjust the quantity, and convert
 * lines into real {@link ManufacturingOrder}s over time without the numbers shifting under them
 * mid-review (component-shortfall figures on each line ARE still computed live against today's
 * stock when the plan is read back, though — see {@code ProductionPlanLineDto} — since a shortfall
 * against current stock is only useful if it reflects current stock).
 */
@Getter
@Setter
@Entity
public class ProductionPlan extends BaseEntity {

    /** Auto-assigned at creation, same "PLAN-" + millis shape as every other document number in this app. */
    private String planNumber;

    /** The low-stock threshold this run was generated with — see ProductionPlanLine.demandFromLowStock's javadoc. Snapshotted so a later change to what threshold an admin favors doesn't reinterpret an old plan's own numbers. */
    private int lowStockThreshold;

    private String note;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductionPlanLine> lines = new ArrayList<>();
}
