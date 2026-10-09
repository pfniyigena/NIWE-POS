package com.posapp.credit;

import com.posapp.common.BaseEntity;
import com.posapp.customer.Customer;
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
 * A single payment a customer makes against their credit account (Tasks: "Customer payment
 * later" / "Partial settlement" / "Multiple payments against one order" / "Payment allocation to
 * orders"). Deliberately its own entity, not another row on the existing {@code Payment} (which
 * is a checkout-time tender on one {@code SaleOrder}) — a customer settling their tab isn't tied
 * to any single order the way a checkout payment is; it's split across whichever outstanding
 * orders it's allocated to, via {@link CustomerPaymentAllocation}. ADMIN-only end to end (see
 * {@code CustomerCreditController}), per the confirmed scope: enabling credit, setting the limit,
 * and settling a customer's tab all stay inside the existing ADMIN-only customer management area.
 *
 * <p>Every dollar of {@code amount} must be allocated to a specific order — see
 * {@code CustomerCreditService.recordPayment}'s "allocated total must equal the payment amount"
 * rule. There's no floating/unapplied credit balance concept in this design (see the README's
 * Known limitations): a payment that isn't fully allocated across the customer's outstanding
 * orders is rejected rather than left partially applied.
 */
@Getter
@Setter
@Entity
public class CustomerPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    private BigDecimal amount;

    private Instant paidAt;

    /**
     * Which payment method this settlement came in as — CASH, CHEQUE, etc (Task: "To record
     * Payment in Sale Order add payment mode"). Reuses the same admin-configurable {@code
     * PaymentMethodEntity}/code system checkout and POS refunds already validate against
     * ({@code PaymentMethodService.validateActiveCodeOrThrow}), rather than a separate enum —
     * one list of methods for the whole app. Nullable only so a payment recorded before this
     * field existed still reads back correctly; every new payment is required to supply one (see
     * {@code RecordPaymentRequest.method}).
     */
    private String method;

    /** Free-text note (e.g. a reference/cheque number, "cash at counter") — purely informational. */
    private String note;

    /** Assigned at record-time, same "PREFIX-timestamp" convention as SaleOrder.invoiceNumber, so a payment receipt has a stable identifier distinct from any order's invoice number. */
    private String receiptNumber;

    /** The admin who recorded this payment — nullable only for defensiveness; every real payment has one, since recording a payment is ADMIN-only. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser recordedBy;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerPaymentAllocation> allocations = new ArrayList<>();

    /**
     * Fourier: "How business branches can be introduced?" — the branch whose document(s) this payment settles; null when it settled documents of several branches (counted as head office).
     * Null = head office, which is what a row created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
