package com.posapp.warehouse;

/**
 * Fourier: "What is we give location type of RECEIVING,SALES,STORAGE,DAMAGED" — replaces the
 * earlier plain {@code WarehouseLocation.isDefault} flag entirely. Per Fourier's own decision
 * ("Type drives behavior directly"), the type is not just a label — it drives real routing:
 *
 * <ul>
 *   <li>{@link #RECEIVING} — where a Purchase Order/Return lands stock on receipt/reversal (see
 *       {@code PurchaseOrderService#receiveLines}, {@code PurchaseReturnService#processReturn}).
 *       Exactly one per warehouse, enforced at creation ({@code WarehouseService#createLocation})
 *       and effectively immutable once set (can never be retyped away, same "never through plain
 *       create/update" treatment {@code Warehouse.primary} already got) — see {@code
 *       WarehouseService#setLocationType}.</li>
 *   <li>{@link #SALES} — a location a sale/checkout can actually target: the only type a register
 *       can be assigned as its {@code defaultLocation} (see {@code Register}), and the only type
 *       {@code OrderService}/{@code ReturnsService} resolve a sale against. Every warehouse needs
 *       at least one *active* SALES location at all times — enforced by refusing to deactivate or
 *       retype away from SALES when it's the last active one for that warehouse.</li>
 *   <li>{@link #STORAGE} — a plain holding area; no special routing, just a Stock Transfer
 *       destination/source like any other location.</li>
 *   <li>{@link #DAMAGED} — per Fourier's own decision ("Excluded from sellable stock"), stock here
 *       is tracked but never counted as available/sellable: checkout, low-stock alerts, and {@code
 *       Product.stockQuantity} all ignore it (see {@code WarehouseService#getStockQuantity(Product,
 *       Warehouse)} / {@code ProductStockRepository#sumSellableQuantityByProductAndWarehouse}). A Stock
 *       Transfer is the only way to move it back out once it's usable again.</li>
 * </ul>
 *
 * <p>Migration (Fourier: "Main Stock becomes SALES; new empty Receiving is added"): every
 * warehouse's pre-existing "Main Stock" default location becomes {@link #SALES} — so everything
 * already sellable stays immediately sellable — and a brand-new, empty {@link #RECEIVING}
 * location is created per warehouse; only *future* purchases flow through it. See {@code
 * WarehouseService#migrateLocationTypesIfNeeded}.
 */
public enum LocationType {
    RECEIVING,
    SALES,
    STORAGE,
    DAMAGED
}
