package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import com.posapp.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single dine-in "tab" opened against one {@link DiningTable} (Fourier: "add Restaurant
 * management in our ERP"). Deliberately its own self-contained order rather than reusing {@code
 * com.posapp.order.SaleOrder} — that entity is tied to a cashier's till session, which doesn't fit
 * how a table stays open across however long a party dines; Fourier's own choice, when asked, was
 * a dedicated screen for this, kept separate from the walk-up POS terminal. See {@link
 * RestaurantOrderService#checkout} for the checkout flow that still decrements stock and posts to
 * Accounting exactly like a POS sale does.
 *
 * <p>Fourier: "Delivery & takeaway" — the same order/kitchen/checkout/accounting machinery also
 * serves TAKEAWAY and DELIVERY orders (see {@link RestaurantOrderType}), which have no {@code
 * table} and instead carry a free-text customer name/phone (and, for DELIVERY, an address and a
 * driver hand-off lifecycle — see {@link DeliveryStatus}).
 */
@Getter
@Setter
@Entity
public class RestaurantOrder extends BaseEntity {

    /** Required for DINE_IN, always null for TAKEAWAY/DELIVERY — see {@link RestaurantOrderType}. */
    @ManyToOne(fetch = FetchType.LAZY)
    private DiningTable table;

    @Enumerated(EnumType.STRING)
    private RestaurantOrderStatus status = RestaurantOrderStatus.OPEN;

    /** Fourier: "Delivery & takeaway" — defaults to DINE_IN so every order opened before this field existed reads as the original table-tab flow. */
    @Enumerated(EnumType.STRING)
    private RestaurantOrderType orderType = RestaurantOrderType.DINE_IN;

    private int guestCount = 1;

    /** Who's serving this table — optional; any logged-in staff member can still act on the order regardless of who's assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser server;

    /** Free-text, same convention as Reservation's own guest fields — set for TAKEAWAY/DELIVERY, unused for DINE_IN. */
    private String customerName;
    private String customerPhone;
    /** Only meaningful for DELIVERY. */
    private String customerAddress;

    /** Who's carrying a DELIVERY order — optional even once assigned a driver, e.g. before dispatch; null for DINE_IN/TAKEAWAY. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser driver;

    /** Only ever set on DELIVERY orders — see {@link DeliveryStatus}. */
    @Enumerated(EnumType.STRING)
    private DeliveryStatus deliveryStatus;

    private String notes;

    /**
     * Hotel module: the in-house guest's booking when this is a room-service order, or the booking the bill was charged to
     * at checkout. Null for every ordinary order (and for every row created before the hotel module existed).
     */
    @jakarta.persistence.Column(name = "room_booking_id")
    private Long roomBookingId;

    /** Hotel module: "101 — Jane Doe", shown on the kitchen ticket for a room-service order. */
    @jakarta.persistence.Column(name = "room_label")
    private String roomLabel;

    private Instant openedAt = Instant.now();
    private Instant closedAt;

    /** Assigned at checkout — own numbering, same "own prefix, own sequence" convention as SaleOrder/Quotation/Invoice's own document numbers. */
    private String ticketNumber;

    /**
     * Fourier: warehouse filter on Stock Movement History — which warehouse this order drew took stock
     * from, snapshotted when it happened (the Main warehouse is stored as null, which is also what
     * every row created before this column existed reads as). A snapshot rather than derived from
     * the register/setting later, because registers get reassigned and settings change.
     */
    @jakarta.persistence.Column(name = "stock_warehouse_id")
    private Long stockWarehouseId;

    /**
     * Fourier: "How business branches can be introduced?" — the branch of the kitchen warehouse this order was prepared from.
     * Snapshot id (not a live reference); null reads as head office, which is also what every row
     * created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RestaurantOrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RestaurantPayment> payments = new ArrayList<>();

    /** Fourier: "Restaurant should impact Inventory Module" — one row per ingredient consumed at checkout for a recipe-backed line; see {@link RestaurantOrderConsumption}'s own javadoc. Empty for an order with no recipe-backed lines. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RestaurantOrderConsumption> ingredientConsumptions = new ArrayList<>();

    /**
     * What the Kitchen Display / audit log should show in place of a table name — the table's own
     * name for DINE_IN, else "Takeaway"/"Delivery" plus the customer name when one was given. Used
     * anywhere this order previously assumed {@code table} was always present.
     */
    public String displayLabel() {
        if (table != null) {
            return table.getName();
        }
        if (roomLabel != null && !roomLabel.isBlank()) {
            return "Room service — " + roomLabel;
        }
        String prefix = orderType == RestaurantOrderType.DELIVERY ? "Delivery" : "Takeaway";
        return (customerName != null && !customerName.isBlank()) ? prefix + " — " + customerName : prefix;
    }
}
