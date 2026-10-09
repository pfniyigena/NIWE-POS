package com.posapp.warehouse;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * How much of one {@link Product} sits in one {@link WarehouseLocation}. Originally keyed
 * directly off {@link Warehouse} in the foundation phase; Fourier's "Add warehouse location and
 * allowing a warehouse to have a default 'Main Stock' location" moved the granularity one level
 * down to location, since every warehouse always has at least that one default location now (see
 * WarehouseLocation's own javadoc). The old {@code warehouse_id} column is left in place on the
 * real table (Hibernate's {@code ddl-auto=update} never drops columns) and is no longer mapped
 * here; any pre-existing row it held was migrated onto the owning warehouse's new default
 * location by {@code WarehouseService#migrateProductStockToLocations} — see that method's own
 * javadoc for the one-time migration itself. {@code Product.stockQuantity} stays authoritative
 * for every existing stock-moving code path until a later phase moves that logic to read/write
 * rows here directly for a chosen location instead.
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "location_id"}))
public class ProductStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseLocation location;

    private int quantity = 0;
}
