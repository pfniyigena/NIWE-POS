package com.posapp.settings;

import com.posapp.accounting.ChartCountry;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Singleton store-wide configuration (Task family: "Business Details" / "System settings", under
 * Admin > Settings). There's exactly one row of this in practice — {@link StoreSettingsService}
 * always fetches the first (and creates it on first use, mirroring how {@code DataSeeder} seeds
 * it up front) rather than this being a per-something list like TaxRate/PaymentMethodEntity, so
 * there's no "code" or "active" flag here the way those have.
 *
 * <p>Split into two request DTOs (see {@code BusinessDetailsRequest}/{@code SystemSettingsRequest})
 * purely because the two admin screens that edit this row (Business Details, System settings) are
 * independent forms — both still read/write the same single row underneath.
 */
@Getter
@Setter
@Entity
public class StoreSettings extends BaseEntity {

    // ---- Business details (Task: "name, address, contact, TAX ID, logo") ----

    private String businessName;
    private String address;
    private String phone;
    private String email;

    /** VAT/tax registration number printed on receipts/invoices — purely informational, like Customer.taxId. */
    private String taxId;

    /**
     * A data: URL (e.g. {@code data:image/png;base64,...}) — the frontend reads the chosen image
     * file with FileReader.readAsDataURL and sends the result as-is, so this stores whatever
     * browsers produce with no separate file-storage/CDN setup needed. Can be a few hundred KB of
     * text, hence TEXT rather than the default varchar(255).
     */
    @Column(columnDefinition = "TEXT")
    private String logoDataUrl;

    // ---- System settings (Task: "currency, time zone, date format") ----

    /** ISO 4217 code, e.g. "USD", "UGX" — kept separate from the symbol since the symbol is what's actually displayed. */
    private String currencyCode = "USD";

    /** What's actually shown next to amounts, e.g. "$", "USh" — editable independently of currencyCode (some admins prefer a code instead of a symbol). */
    private String currencySymbol = "$";

    /** An IANA zone id, e.g. "Africa/Kampala" — see SystemSettingsComponent for how the picker is populated. */
    private String timeZone = "UTC";

    /** A display pattern, e.g. "dd/MM/yyyy" — see SystemSettingsComponent for the fixed set of choices offered. */
    private String dateFormat = "dd/MM/yyyy";

    /**
     * Task 1: "In the system setting configure the max discount" — a store-wide ceiling on any
     * single order line's {@code OrderLine.discountPercent}, enforced by
     * {@code OrderService.validateDiscount} for both the normal online update-line flow and the
     * offline-sale replay path (see that method's javadoc). Applies to every discount regardless
     * of who's applying it — see Task 2 (Customer.creditEnabled's admin-only pattern) for the
     * separate, role-based restriction on *who* may give a discount at all; this field only caps
     * *how much*. Defaults to 100 (effectively unrestricted) so an existing deployment that never
     * visits System settings keeps today's behavior until an admin actually narrows it.
     */
    private BigDecimal maxDiscountPercent = BigDecimal.valueOf(100);

    /**
     * Fourier: "Same for discount (but discount should be activate in the setting or
     * configuration) and it will be per line" — then "Except POS other module can have global
     * discount. But again give the user to configure both discount. Either on line or global" —
     * the master switch for how every non-POS line-item document (Quotation, Sales Order,
     * Invoice, Purchase Order — Bill/Refund/Credit Note inherit whatever their source Sales
     * Order/Purchase Order already carried, same "freeze what was actually ordered" convention
     * {@code taxCode} snapshotting already follows) applies a discount: {@code NONE} (no
     * discount concept at all), {@code PER_LINE} (each line gets its own, see {@code
     * QuotationLine.discountPercent}'s javadoc), or {@code GLOBAL} (one percentage entered once
     * for the whole document, see {@code Quotation.discountPercent}'s javadoc) — mutually
     * exclusive, never both at once. The POS terminal's own discount ({@code
     * OrderLine.discountPercent}) is unaffected by this setting entirely — it predates it and is
     * gated purely by role (see {@code OrderService.validateDiscount}). Defaults to {@code NONE}:
     * unlike {@link #maxDiscountPercent} (which only caps an amount that was already possible),
     * this is a brand new capability on document types that never had one, so an existing
     * deployment that never visits System settings sees no change in behavior — an admin has to
     * explicitly pick a mode. Once set to {@code PER_LINE} or {@code GLOBAL}, {@link
     * #maxDiscountPercent} still applies as the same store-wide ceiling, shared with the POS
     * terminal's own discount cap (see {@code StoreSettingsService.validateLineDiscount}/{@code
     * .validateGlobalDiscount}).
     */
    @Enumerated(EnumType.STRING)
    private DiscountMode discountMode = DiscountMode.NONE;

    /**
     * Task: "Inventory Setting (Example: allow negative stock)." Whether a checkout may sell more
     * of a product than {@code Product.stockQuantity} currently shows, and whether a manual {@code
     * StockAdjustment} may push a product's stock below zero (see {@code
     * OrderService.validateStockAvailability}/{@code StockAdjustmentService.create}). Defaults to
     * {@code true} — before this setting existed, stock was never checked anywhere in this app (see
     * {@code OrderService.syncOfflineSale}'s own javadoc, which still applies unchanged: an offline
     * sale is never rejected for this, since it already happened for real by the time it syncs), so
     * defaulting to {@code true} means an existing deployment that never visits System settings
     * keeps exactly today's behavior, same reasoning as {@code maxDiscountPercent} defaulting to an
     * effectively-unrestricted 100. An admin running tighter inventory control turns this off.
     */
    private boolean allowNegativeStock = true;

    /**
     * Fourier: "Product Packaging &amp; Breakdown / Unit Conversion ... Whiskey Breakdown &amp;
     * Beer Breakdown (in the bar a bottle of whiskey can be splited into shot. the same a caisse
     * of beer can be sold in bottle) But this will be configured as we did for chart of accounts.
     * If not activated ignore packaging" — the master switch for actually running a {@code
     * com.posapp.packaging.PackagingRule} (see {@code PackagingService#breakdown}). Configuring a
     * rule itself (parent product, child product, how many child units one parent yields) is
     * never gated by this flag, same "configuration vs. execution" split {@code Bom} already has
     * from {@code ManufacturingOrderService#produce} — only the stock-moving action is. Defaults
     * to {@code false} — this is a brand-new capability (splitting one product's stock into
     * another), so an existing deployment that never visits Inventory settings sees no change in
     * behavior until an admin explicitly turns it on, same reasoning as {@link #discountMode}
     * defaulting to {@code NONE}.
     */
    private boolean productBreakdownEnabled = false;

    /**
     * Fourier: "Add allow multi-warehouse in inventory settings. By default multi-warehouse is
     * disabled. if disabled: 1. one warehouse(default) and one location(default) 2. Terminal
     * linked to this warehouse(default) ... if yes: 1. All purchase will impact default location
     * in default Main warehouse. 2. No sale can be made at default location/Main warehouse. 3. To
     * make sale there is a need of creating another warehouse and locations and Terminal will be
     * linked to those warehouse." Followed up: "When multi-warehouse is OFF (the default) but I
     * can still add location on the default warehouse" — so this flag only ever gates creating a
     * *second warehouse* ({@code WarehouseService#create}) and whether a sale may complete
     * against the primary warehouse ({@code OrderService#assertSellingWarehouseAllowed});
     * creating further {@code WarehouseLocation}s on the existing (default) warehouse, moving
     * stock between locations (Stock Transfers), and a purchase always landing on the primary
     * warehouse's default location are all unaffected by this flag either way — see each of
     * those methods' own javadoc. Defaults to {@code false} — before this setting existed there
     * was only ever one warehouse anyway, so an existing deployment that never visits Inventory
     * settings sees no change in behavior, same reasoning as {@link #productBreakdownEnabled}.
     */
    private boolean multiWarehouseEnabled = false;

    /**
     * Fourier: multi-warehouse phase 4 — the one "kitchen" location Restaurant orders (menu items
     * and recipe ingredients) and Restaurant wastage draw stock from. A plain id rather than a
     * relation so this settings row stays free of warehouse dependencies; validated when saved (see
     * StoreSettingsController) and re-checked when used (WarehouseService#getRestaurantLocationOrNull).
     * Null = the primary warehouse's normal stock, exactly as before this setting existed.
     */
    @jakarta.persistence.Column(name = "restaurant_location_id")
    private Long restaurantLocationId;

    // ---- Email/SMTP settings (Fourier: "Configure smtp for sending email ... sending other
    // documents like quotations, invoices, receipts, etc to the customer's email") ----

    /** Master on/off switch — see MailService.isConfigured's javadoc for why every send checks this rather than just a non-null host. */
    private boolean smtpEnabled = false;

    private String smtpHost;

    /** Defaults to 587 (STARTTLS) — the most broadly-supported authenticated-SMTP port; see smtpUseSsl's javadoc for the 465/587 distinction. */
    private Integer smtpPort = 587;

    private String smtpUsername;

    /**
     * Never sent back to the frontend (see StoreSettingsDto, which exposes only
     * `smtpPasswordSet`) — a saved SMTP password is write-only from the admin UI's point of view,
     * same "never resend secrets" reasoning as a login password. {@code EmailSettingsRequest}'s
     * password field is optional on update for the same reason: leaving it blank keeps whatever's
     * already saved instead of clearing it just because the form re-submitted an empty string.
     */
    private String smtpPassword;

    /** What recipients see in the "From" header — falls back to smtpUsername in MailService if blank. */
    private String smtpFromEmail;

    /** What recipients see as the sender's display name — falls back to businessName in MailService if blank. */
    private String smtpFromName;

    /**
     * true = connect with implicit TLS from the start (port 465 is the common convention);
     * false = plain connection upgraded via STARTTLS (port 587, the more common default for most
     * providers including Gmail's and Office 365's own documented setup). Either works with
     * whatever smtpPort is actually set — this only picks which handshake MailService uses.
     */
    private boolean smtpUseSsl = false;

    /**
     * Fourier: hit a genuine certificate-hostname mismatch on a real mail host (mangatek.rw) that
     * no client-side JavaMail connection-setup change could route around — the server's own TLS
     * certificate simply doesn't list the configured host name, confirmed by trying two different
     * JavaMail SSL setups with the identical failure both times (see MailService.buildSender's
     * javadoc). This is the deliberate, opt-in escape hatch for exactly that situation: still
     * encrypted, but the app stops verifying the certificate actually belongs to smtpHost, which
     * makes it vulnerable to a man-in-the-middle if the network path to that host isn't
     * trustworthy. Defaults to {@code false} — an admin has to explicitly turn this on knowing the
     * tradeoff (see EmailSettingsComponent's own warning copy), never silently.
     */
    private boolean smtpSkipCertCheck = false;

    /**
     * Fourier: "Wiring a handful of business events to that same mailer" — the one address every
     * passive-alert email (low stock, a requisition awaiting approval, a quotation about to
     * expire, a customer nearing their credit limit) goes to; see {@code
     * com.posapp.alerts.AlertMailService}. Nullable — alerts simply don't send while this is
     * blank, same "presence is the opt-in, no separate toggle needed" convention {@code
     * smtpHost}/{@code isConfigured} already uses for SMTP itself.
     */
    private String notificationsEmail;

    /**
     * How a product's cost price follows purchases: WEIGHTED_AVERAGE (default — blends each delivery into
     * the existing cost by quantity), LAST_PURCHASE (the latest delivery's unit cost replaces it) or MANUAL
     * (never changed automatically). Null on rows created before this setting = WEIGHTED_AVERAGE.
     */
    @jakarta.persistence.Column(name = "costing_method")
    private String costingMethod = "WEIGHTED_AVERAGE";

    // ---- Accounting setup (Fourier: "The Config app.accounting.chart-country can be forgotten
    // easily, what if we add this on the screen then at first time a user select the country?") ----

    /**
     * The country whose starter chart of accounts this store was actually seeded with — set once,
     * by whichever path first seeds the chart (normally {@code ChartSetupController}'s on-screen
     * wizard; see {@code AccountService.seedDefaultChartIfNeeded}), and never changed afterward.
     * Null only in the brief window before any account exists yet, which is exactly when the
     * wizard is shown instead of the normal admin screens. This is the durable answer to "which
     * country's chart is this store on" — {@code app.accounting.chart-country} only matters before
     * this field is set (as the wizard's pre-filled suggestion, and as the safety-net country if a
     * posting forces a lazy seed before anyone visits the wizard), so editing that property
     * afterward has no effect, unlike before this field existed.
     */
    @Enumerated(EnumType.STRING)
    private ChartCountry chartCountry;
}
