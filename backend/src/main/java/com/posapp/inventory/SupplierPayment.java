package com.posapp.inventory;

import com.posapp.common.BaseEntity;
import com.posapp.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * "Add Supplier payment." A single payment this store makes to a supplier against what it owes
 * them — the accounts-payable mirror of {@code com.posapp.credit.CustomerPayment}, right down to
 * the allocation model: every dollar of {@code amount} must be allocated to one or more specific
 * {@link PurchaseOrder}s via {@link SupplierPaymentAllocation}, capped at what's still owed on
 * each (see {@code SupplierPaymentService.recordPayment}) — no floating/unapplied balance
 * concept here either, same reasoning as the customer side.
 *
 * <p>What a PO "owes" in the first place only exists once it's actually been committed to the
 * supplier — a DRAFT PO hasn't been sent, so nothing is owed on it yet; see {@code
 * SupplierPaymentService.amountOwedForOrder}. {@code receiptNumber} uses its own {@code "SPMT-"}
 * prefix (distinct from {@code CustomerPayment}'s {@code "PMT-"}) so the two payment kinds' own
 * identifiers never collide even though they're unrelated tables.
 */
@Getter
@Setter
@Entity
public class SupplierPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    private BigDecimal amount;

    private Instant paidAt;

    /**
     * Which payment method this settlement went out as — CASH, BANK TRANSFER, etc (Fourier: "Add
     * payment method when recording bill payment"). Reuses the same admin-configurable {@code
     * PaymentMethodEntity}/code system {@code CustomerPayment.method} already validates against —
     * see that field's own javadoc. Nullable because only {@code
     * SupplierPaymentService.recordBillPayment} sets/requires one so far — a payment recorded
     * through the general supplier-wide allocation screen ({@code recordPayment}) still leaves
     * this null, same as any payment recorded before this field existed.
     */
    private String method;

    /** Free-text note (e.g. a reference/cheque number, "bank transfer") — purely informational. */
    private String note;

    private String receiptNumber;

    /** The admin who recorded this payment — same "ADMIN-only action, so this is never null for a real payment" reasoning as CustomerPayment.recordedBy. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser recordedBy;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupplierPaymentAllocation> allocations = new ArrayList<>();

    /**
     * Fourier: "How business branches can be introduced?" — the branch whose bill(s)/order(s) this payment settles; null when it settled documents of several branches (counted as head office).
     * Null = head office, which is what a row created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
