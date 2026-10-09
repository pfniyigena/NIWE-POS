package com.posapp.warehouse;

import com.posapp.branch.Branch;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * Fourier: "multi-location/multi-warehouse tracking ... needed the moment there's a second
 * physical shop or stockroom" — foundation phase. A real location entity, replacing what used to
 * be nothing at all ({@code Register.location} is just a free-text label on a till, never a thing
 * stock is actually tracked against). Deliberately a simple admin-managed lookup list for now —
 * same "name + active flag" shape as {@code com.posapp.helpdesk.HelpDeskCategory}/{@code
 * com.posapp.catalog.Category} — not yet wired into any stock-moving flow (that's the next phase);
 * this phase only introduces the entity itself, seeds exactly one ({@link #primary}) automatically,
 * and mirrors every existing product's {@code stockQuantity} into a {@link ProductStock} row
 * against it, so the schema exists and is populated correctly before anything starts reading or
 * writing it per-location.
 *
 * <p>{@link #primary} marks the one warehouse every pre-existing stock-moving code path (POS
 * checkout, stock adjustments, purchase receipts, manufacturing, packaging breakdown, restaurant
 * consumption, ...) implicitly targets until each of those is updated, in a later phase, to let
 * the caller pick a warehouse — see {@code WarehouseService#syncDefaultWarehouseStock}'s own
 * javadoc. Exactly one warehouse ever carries this flag; it is set only by {@code
 * WarehouseService#seedDefaultWarehouseIfNeeded} on first startup, never by the admin-facing
 * create/update endpoints (see {@code WarehouseRequest}, which deliberately has no field for it),
 * and {@code WarehouseService#setActive} refuses to deactivate it.
 */
@Getter
@Setter
@Entity
public class Warehouse extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    /** Free text — street/area, nothing structured. Nullable; plenty of stores have exactly one obvious location and nothing to write here yet. */
    private String address;

    private boolean active = true;

    /**
     * See this class's own javadoc. Explicit {@code @Column(name = "is_primary")}: the bare
     * property name {@code primary} is a reserved SQL keyword in PostgreSQL (it's part of
     * {@code PRIMARY KEY}), so an unquoted {@code CREATE TABLE warehouse (... primary boolean ...)}
     * fails with a syntax error — Hibernate's {@code ddl-auto=update} logs that failure and keeps
     * going rather than aborting startup, which is exactly how this table ended up silently never
     * created on a fresh database. Spring Data's derived query methods (e.g.
     * {@code WarehouseRepository.findByPrimaryTrue}) key off the Java property name, not this
     * column name, so nothing else needs to change.
     */
    @Column(name = "is_primary")
    private boolean primary = false;

    /**
     * Fourier: per-warehouse low-stock threshold. Nullable: null means "use the store-wide default"
     * ({@code app.pos.low-stock-threshold}), which is what every warehouse did before this column
     * existed. When set, it drives this warehouse's low-stock email alert, its dashboard low-stock
     * list and the Stock On Hand report's "Low stock" status for it; see
     * {@code WarehouseService#effectiveLowStockThreshold}.
     */
    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    /**
     * Fourier: "How business branches can be introduced?" — the branch this warehouse belongs to.
     * Nullable at the DB level (existing rows arrive null under {@code ddl-auto=update}); the
     * startup backfill assigns head office, and null is always read as head office.
     */
    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
