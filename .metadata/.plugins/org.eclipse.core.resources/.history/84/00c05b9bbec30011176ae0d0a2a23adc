package com.posapp.order;

import com.posapp.common.BaseEntity;
import com.posapp.customer.Customer;
import com.posapp.register.CashierSession;
import com.posapp.register.RegisterSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class SaleOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RegisterSession session;

    /**
     * Which cashier's own working session (within {@link #session}'s shift) this cart/sale
     * belongs to — nullable because orders created before this feature existed have none. See
     * {@code com.posapp.register.CashierSession}'s javadoc: this is attribution only, not an
     * access-control boundary.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private CashierSession cashierSession;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.OPEN;

    /** Free-text note set by the "Park" action (e.g. why the sale was set aside) — purely informational. */
    private String note;

    /** Assigned at checkout; null while the order is still an open cart. */
    private String invoiceNumber;

    private Instant completedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    /**
     * How much of this completed sale was tendered on store credit — snapshotted once at checkout
     * as the sum of payments whose method is flagged credit (see PaymentMethodEntity.credit),
     * following this codebase's existing "snapshot, don't live-derive" convention (same as tax/cost
     * on OrderLine): if a payment method's credit flag is later changed, or the method deactivated,
     * this order's own credit amount must never move. Null for orders with no credit portion at all
     * (including every order predating this feature) — never zero-vs-null ambiguity is needed
     * because CustomerCreditService always null-checks before summing. Task family: "Cash + credit
     * partial payment" / "100% credit sale".
     */
    private BigDecimal creditAmount;

    /**
     * When the credit portion of this sale is due (Task: "Optional due date/payment terms") — set
     * once at checkout from either an explicit request value or Customer.defaultCreditTermDays;
     * never recomputed afterward. Null means "no due date" (never overdue), which is also the only
     * possible value when {@link #creditAmount} is null.
     */
    private LocalDate dueDate;

    /**
     * Task: "local queuing of sales that sync once connectivity returns" — a client-generated
     * dedupe key (a UUID from the terminal's offline queue), set only for a sale rung while
     * offline and replayed here once connectivity returns. Nullable-but-unique, same convention as
     * Product.code: null for every ordinary online sale (the overwhelming majority), unique for the
     * rest so a retried sync request (the terminal never got the first response, or retried after a
     * timeout) finds the already-created order instead of double-selling — see
     * OrderService.syncOfflineSale.
     */
    @Column(unique = true)
    private String clientTempId;

    /**
     * When this sale actually happened at the terminal — the cashier's own clock, at the moment
     * they rang it up offline — as opposed to {@link com.posapp.common.BaseEntity#getCreatedAt()}
     * and {@link #completedAt}, which are both stamped at *sync* time (whenever connectivity
     * happened to return, possibly hours later). Null for every ordinary online sale.
     */
    private Instant offlineQueuedAt;

    /**
     * Set only when an offline-synced sale's server-computed total doesn't match what the
     * terminal estimated while offline (product prices/tax are cached locally and can drift
     * before the sale actually syncs) — plain text, surfaced on the receipt/Receipts list so it's
     * actually reviewable rather than silently accepted. Null for every ordinary online sale, and
     * for an offline sale whose estimate happened to match. See OrderService.syncOfflineSale —
     * "sync it anyway, flag for review" was the deliberate choice here: stock is never checked at
     * checkout at all (online or offline), so there's nothing to flag there, only price/tax drift.
     */
    @Column(columnDefinition = "TEXT")
    private String syncNote;

    /**
     * Fourier: warehouse filter on Stock Movement History — which warehouse this sale took stock
     * from, snapshotted when it happened (the Main warehouse is stored as null, which is also what
     * every row created before this column existed reads as). A snapshot rather than derived from
     * the register/setting later, because registers get reassigned and settings change.
     */
    @jakarta.persistence.Column(name = "stock_warehouse_id")
    private Long stockWarehouseId;

    /**
     * Fourier: "How business branches can be introduced?" — the branch this sale belongs to, taken from the selling register's warehouse.
     * Snapshot id (not a live reference); null reads as head office, which is also what every row
     * created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;

    /**
     * EFRIS (Rwanda RRA e-invoicing) fiscal receipt data — Fourier's own follow-up spec: "When
     * installing EFRIS the system will add the following column to SaleOrder." All eight fields
     * are nullable TEXT, populated only once a sale has actually been submitted to the EFRIS app
     * (see {@code com.posapp.apps.efris}) and RRA's Sales Data Controller has responded for it.
     * Every sale rung before EFRIS is wired into checkout, and every sale at a store that never
     * installs/enables the EFRIS app at all, has all eight as null — this pass adds exactly the
     * columns specified and surfaces them on the receipt once populated; it deliberately does not
     * yet call any real EFRIS/SDC endpoint to populate them (see the project doc for what's still
     * deferred and why).
     */
    @Column(name = "sdc_id", columnDefinition = "TEXT")
    private String sdcId;

    @Column(name = "sdc_date", columnDefinition = "TEXT")
    private String sdcDate;

    @Column(name = "sdc_receipt_number", columnDefinition = "TEXT")
    private String sdcReceiptNumber;

    @Column(name = "internal_data", columnDefinition = "TEXT")
    private String internalData;

    @Column(name = "signature_data", columnDefinition = "TEXT")
    private String signatureData;

    @Column(name = "cis_number", columnDefinition = "TEXT")
    private String cisNumber;

    @Column(name = "cis_date", columnDefinition = "TEXT")
    private String cisDate;

    @Column(name = "mrc_data", columnDefinition = "TEXT")
    private String mrcData;

    /**
     * When an EFRIS submission was last attempted for this sale (success or failure) — null means
     * never attempted (EFRIS disabled, this terminal has no EfrisSetting configured, or the sale
     * predates this feature). See {@link #efrisError}.
     */
    private Instant efrisSubmittedAt;

    /**
     * Set only when an EFRIS submission was attempted and failed (network error, unreachable
     * endpoint, or EFRIS itself rejected the invoice) — plain text, surfaced the same way
     * {@link #syncNote} already is (Receipts list, the receipt reprint screen) so it's actually
     * reviewable rather than silently dropped. This is the "sale still completes, flagged for
     * review" policy: a failed EFRIS submission never blocks or rolls back the sale itself, it
     * just means the eight fiscal columns above stay null until someone retries. Null on success,
     * and null for every sale where no submission was attempted at all.
     */
    @Column(columnDefinition = "TEXT")
    private String efrisError;

    /**
     * The raw EFRIS response body from the most recent submission attempt — Fourier's own
     * request, "also record Efris response" — kept verbatim (whatever JSON/text EFRIS actually
     * sent back) alongside the parsed fiscal fields/{@link #efrisError}, on both success and
     * failure, whenever an HTTP response was actually received. Null when no submission was
     * attempted, or when the attempt never got as far as a response (e.g. no EFRIS URL
     * configured, or the request couldn't be sent at all).
     */
    @Column(columnDefinition = "TEXT")
    private String efrisResponse;
}
