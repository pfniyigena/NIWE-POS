package com.posapp.bill;

import com.posapp.common.BaseEntity;
import com.posapp.common.GlobalDiscountType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fourier: "The Purchase order should flow same workflow as Sale order (with Bills and Refunds
 * equivalent with Invoices and Credit note)." A formal, numbered bill generated from one {@code
 * com.posapp.inventory.PurchaseOrder} — the accounts-payable mirror of {@code
 * com.posapp.invoice.Invoice}, holding {@link #purchaseOrderId} as a plain {@code Long} (never a
 * JPA {@code @ManyToOne}) for exactly the same reason that field stays decoupled there: so this
 * module has zero entity coupling to {@code com.posapp.inventory}, reaching across that boundary
 * only through {@code PurchaseOrderService}'s own public {@code getById}. {@code supplierId}/
 * {@code supplierName}/{@code purchaseOrderNumber} are denormalized at generation time for the
 * same "no live join needed for display" reasoning every other snapshot field in this app
 * follows.
 * <p>
 * Unlike {@code Invoice}, every {@code Bill} is generated from a Purchase Order — Fourier never
 * asked for a "bill with no purchase order behind it" the way a standalone invoice exists, and one
 * wouldn't have any received stock to anchor it to. {@code BillService.generate}/{@code
 * generateDraftFromPurchaseOrder} refuse a second bill for the same order (one bill document per
 * PO, same "no replacement after voiding" rule {@code Invoice}'s own class javadoc documents).
 * <p>
 * Once {@code ISSUED}, the only further move is {@code VOID} — refused once anything has been
 * paid against this bill (see {@code SupplierPaymentService.outstandingForBill}); a bill that's
 * already been paid is cancelled via a Purchase Return instead (processed against the underlying
 * Purchase Order, exactly how a paid Sales-Order-derived invoice is cancelled via a Sales Order
 * Return rather than {@code InvoiceService.cancelPaidInvoice}) — so, unlike {@code Invoice}, this
 * entity carries no {@code paymentDisposition}/{@code refundMethod} pair at all; see {@link
 * #refundNumber}'s own javadoc for what "Refund" means here instead.
 * <p>
 * {@code billNumber} is this document's own human-readable identifier, same {@code "XX-" +
 * millis} convention as every other document number in this app — its own {@code "BILL-"} prefix
 * keeps it from ever colliding with {@code PurchaseOrder.orderNumber}'s {@code "PO-"}.
 */
@Getter
@Setter
@Entity
public class Bill extends BaseEntity {

    private String billNumber;

    private Long purchaseOrderId;

    private String purchaseOrderNumber;

    private Long supplierId;

    private String supplierName;

    @Enumerated(EnumType.STRING)
    private BillStatus status = BillStatus.ISSUED;

    private Instant billDate = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String notes;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — a single discount percentage applied
     * uniformly to every line on this bill, carried verbatim from the Purchase Order's own
     * {@code discountPercent} at generation time (see {@code BillLine.discountPercent}'s own
     * javadoc for the PER_LINE case instead). Zero (never null) for a bill with no discount at
     * all, or one entered per line instead.
     */
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
     * default" — how {@link #discountPercent} above was actually entered on the originating
     * Purchase Order while in GLOBAL mode, carried verbatim; purely a display concern, since
     * every line/tax calculation downstream reads only the resolved {@link #discountPercent}.
     * Meaningless (and left at its default) outside GLOBAL mode.
     */
    @Enumerated(EnumType.STRING)
    private GlobalDiscountType discountType = GlobalDiscountType.PERCENT;

    /** Fourier: "Global discount should be a fixed amount or percentage" — the flat currency amount entered on the originating Purchase Order, carried verbatim; null unless {@link #discountType} is {@code FIXED_AMOUNT}. */
    private BigDecimal discountFixedAmount;

    private Instant voidedAt;

    @Column(columnDefinition = "TEXT")
    private String voidReason;

    /**
     * Fourier: "Refunds equivalent with Credit note" — this document's own identifier as a refund,
     * assigned the moment it's voided (own {@code "REF-"} prefix, same convention as {@code
     * billNumber}'s {@code "BILL-"}, so the two numbers are never confused on the printed
     * document). Null until voided; see {@code BillService.voidBill}. Deliberately not a separate
     * entity — exactly how a credit note is just {@code Invoice.status == VOID} plus a few fields
     * on that same row, a refund here is just this {@code Bill} once it reaches {@code VOID}.
     */
    private String refundNumber;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillLine> lines = new ArrayList<>();

    /**
     * Fourier: "How business branches can be introduced?" — the branch this bill belongs to; inherited from its Purchase Order.
     * Null = head office, which is what a row created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
