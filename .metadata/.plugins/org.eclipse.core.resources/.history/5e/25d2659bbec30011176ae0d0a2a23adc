package com.posapp.warehouse;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Fourier: "Add warehouse location and allowing a warehouse to have a default 'Main Stock'
 * location", later refined by Fourier: "What is we give location type of
 * RECEIVING,SALES,STORAGE,DAMAGED" — a sub-area within one {@link Warehouse} (a shelf, bin,
 * receiving area, ...), with real stock tracked against it: {@link ProductStock} now keys off a
 * WarehouseLocation rather than a Warehouse directly (see that class's own javadoc for why, and
 * for how any already-moved stock was migrated onto this).
 *
 * <p>The earlier plain {@link Boolean} "default" flag this class used to carry has been replaced
 * entirely by {@link #type} — see {@link LocationType}'s own javadoc for exactly how each type
 * drives behavior, the "exactly one RECEIVING, at least one active SALES" per-warehouse
 * invariants, and the "Main Stock becomes SALES; new empty Receiving is added" migration that ran
 * once against every pre-existing warehouse (see {@code WarehouseService#migrateLocationTypesIfNeeded}).
 * {@code type} is deliberately left nullable at the JPA level (no {@code nullable = false}) even
 * though every row is backfilled by that migration — this table already has real production rows
 * under {@code spring.jpa.hibernate.ddl-auto=update}, and that setting never runs a destructive
 * {@code ALTER TABLE ... ADD COLUMN ... NOT NULL} against existing data, so a NOT-NULL-in-spirit
 * column still has to arrive nullable and get backfilled by application code instead.
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "name"}))
public class WarehouseLocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Warehouse warehouse;

    @Column(nullable = false)
    private String name;

    private boolean active = true;

    /**
     * See this class's own javadoc and {@link LocationType}'s. Explicit {@code @Column(name =
     * "location_type")}: not because {@code type} is a reserved SQL keyword (it isn't), but
     * defensively — this exact codebase has already been bitten twice by an unquoted reserved-word
     * column name ({@code Warehouse.primary} → {@code is_primary}, this class's own old {@code
     * isDefault} → {@code is_default}), so every new column here gets an explicit name rather than
     * risking a third surprise. Nullable at the JPA level — see this class's own javadoc for why.
     * Never admin-settable through plain create/update for {@link LocationType#RECEIVING} once a
     * warehouse already has one, and never retyped away from {@link LocationType#RECEIVING} or
     * away from {@link LocationType#SALES} when it's a warehouse's last active SALES location —
     * only ever changed through {@code WarehouseService#setLocationType}, which enforces exactly
     * that (same "never through the plain create/update DTO" convention {@code Warehouse.primary}
     * established).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type")
    private LocationType type;
}
