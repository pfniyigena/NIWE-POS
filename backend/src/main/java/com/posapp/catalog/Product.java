package com.posapp.catalog;

import com.posapp.common.BaseEntity;
import com.posapp.tax.TaxRate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class Product extends BaseEntity {

    private String sku;

    /** Nullable: not every product is barcode-scannable, but if set it must be unique for the barcode-lookup add-to-cart flow. */
    private String barcode;

    /**
     * A second, admin-assigned identifier (Task: "add product icon, code. code can be null but
     * unique") — distinct from both {@code sku} (never uniqueness-checked) and {@code barcode}
     * (unique only within the barcode-lookup flow's own query, never enforced at write time).
     * {@code unique = true} with no {@code nullable = false} is deliberate: Postgres unique
     * indexes treat every NULL as distinct from every other NULL, so any number of products can
     * leave this blank while two products can never share the same non-null code — exactly "can
     * be null but unique". CatalogService.applyRequest double-checks this itself before saving
     * (see its javadoc) so a collision comes back as a clean 409 rather than a raw
     * DataIntegrityViolationException.
     */
    @Column(unique = true)
    private String code;

    /**
     * A small picture for this product — shown as a thumbnail in the admin product list/form and
     * on the cashier terminal's product tile. Same "data: URL straight out of FileReader" convention
     * as StoreSettings.logoDataUrl; TEXT rather than the default varchar(255) for the same reason.
     * Nullable and, unlike code, never uniqueness-checked — there's no reason two products
     * couldn't use the same stock photo.
     */
    @Column(columnDefinition = "TEXT")
    private String iconDataUrl;

    private String name;

    /**
     * Free-text description of this product (Fourier: "On Invoice Line add product description
     * text (by default it should pick the product description)") — optional, shown nowhere on the
     * terminal or in the admin product list itself; its one job is to be the default text an
     * {@code InvoiceLine} snapshots in at add-line time (see {@code InvoiceLine.description}'s
     * javadoc), same "nullable, free text, not uniqueness-checked" shape as {@code iconDataUrl}
     * above. TEXT rather than the default varchar(255) since a product description can run long.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal unitPrice = BigDecimal.ZERO;

    /**
     * What this product costs the store per unit (not shown to customers) — used only for the
     * admin-facing Profit reports (revenue minus cost). Defaults to zero for products an admin
     * hasn't filled in yet, which simply reports 100% margin on those until they do; there's no
     * way to infer a real cost otherwise. Snapshotted onto OrderLine.unitCost at add-line time,
     * same reasoning as unitPrice/taxRate above — a later cost edit never rewrites a past sale's
     * reported profit.
     */
    private BigDecimal costPrice = BigDecimal.ZERO;

    private int stockQuantity = 0;

    /**
     * Whether stock quantity actually gates or is moved by selling this product (Fourier: "Add
     * track stock attribute on Product"). Defaults to {@code true} — every existing product keeps
     * behaving exactly as before. When {@code false}, this product is like a service or
     * made-to-order item: it can always be sold regardless of {@code stockQuantity}, and selling it
     * never changes that number. Per Fourier's own confirmed choice ("Skip stock deduction/checks
     * entirely"), this is checked in exactly three places — {@code
     * OrderService.validateStockAvailability}/{@code completeCheckout} (POS checkout), {@code
     * SalesOrderService.fulfillLines} (Sale Order fulfillment), and {@code
     * CatalogService.listLowStock} (low-stock alerts) — and, to keep a return the mirror image of
     * the sale it reverses, the matching restock step in {@code ReturnsService}/{@code
     * SalesOrderReturnService} also skips a non-tracked product's line, since nothing was ever
     * deducted for it to begin with. Purchase-order receiving and manual stock adjustments are
     * deliberately left alone — those are an admin's own explicit inventory actions, not an
     * automatic side effect of a sale, so a non-tracked product can still have its
     * {@code stockQuantity} corrected or received against if an admin chooses to.
     */
    private boolean trackStock = true;

    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    /** The manufacturer/brand of this product (Fourier: "Add product brands") — optional, same nullable-reference convention as category/taxRate. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Brand brand;

    /**
     * Which tax rate applies to this product's line total — nullable, so a product can be
     * deliberately tax-exempt (no rate assigned). Replaces the earlier "one flat rate for every
     * sale" model (see TaxRate's class javadoc): each product now carries its own assignment, and
     * OrderService snapshots it onto OrderLine the moment a line is added, so a later change here
     * never rewrites an already-rung sale.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private TaxRate taxRate;

    /**
     * The commodity/tariff code EFRIS calls "unsp_code" on an invoice line item (a UNSPSC-style
     * classification code) — nullable, purely to feed a real EFRIS submission; nothing else in
     * this app reads it. Snapshotted onto {@code OrderLine.unspCode} at add-line time, the same
     * convention as every other per-line snapshot (taxCode, unitCost, ...).
     */
    private String unspCode;

    /**
     * Whether this product is a service rather than a physical good — Task: "if EFRIS Chad is set
     * then call configured with the shared sample request." EFRIS Chad's invoice line requires an
     * {@code item_nature} of either {@code ITEM_NATURE_GOODS} or {@code ITEM_NATURE_SERVICE}, and
     * this app had no goods/service distinction anywhere before Chad needed one. Defaults to
     * {@code false} (goods) — Fourier's own choice when asked, since most POS sales here are
     * physical products, not services. Snapshotted onto {@code OrderLine.service} at add-line
     * time, same convention as every other per-line snapshot (taxCode, unspCode, unitCost, ...).
     */
    private boolean service = false;

    /**
     * Reorder point: when this product's sellable stock (all warehouses) plus what is already on open
     * purchase orders falls to this level or below, it shows on the Reorder report. Null = no reorder
     * rule for this product.
     */
    @Column(name = "reorder_level")
    private Integer reorderLevel;

    /** Order-up-to level; the suggested order quantity is this minus current stock and open orders. Null = twice the reorder level. */
    @Column(name = "max_stock_level")
    private Integer maxStockLevel;

    /**
     * Set on a variant (e.g. "T-Shirt - Red / L"): the id of the template product it was generated from. A
     * plain id, not a relation. Null for ordinary products and for templates themselves.
     */
    @Column(name = "variant_parent_id")
    private Long variantParentId;

    /** The variant's own option values, e.g. "Red / L". Null unless this product is a variant. */
    @Column(name = "variant_label")
    private String variantLabel;

    /**
     * Set on a template product (the grouping record variants were generated from): a readable summary of its
     * options, e.g. "Size: S, M, L | Colour: Red, Blue". The template itself is kept inactive so only its
     * variants are sold. Null for ordinary products and variants.
     */
    @Column(name = "variant_attributes", columnDefinition = "TEXT")
    private String variantAttributes;

    /** How individual units are traced: null/NONE = plain quantity, BATCH = lots with expiry, SERIAL = one serial number per unit. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "tracking_mode", length = 10)
    private com.posapp.tracking.TrackingMode trackingMode;
}
