package com.posapp.customer;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class Customer extends BaseEntity {
    private String name;
    private String phone;
    private String email;

    /** VAT/tax registration number for this customer (Task: "Add Tax ID on customer entity") —
     * e.g. for issuing a receipt/statement a business customer can claim against their own tax
     * filing. Purely informational; nothing in checkout/tax math reads this field. Nullable —
     * most walk-in customers won't have one. */
    private String taxId;

    /**
     * Whether this customer may buy on credit at all (Task: "Enable credit for customer") —
     * ADMIN-only to set (see CustomerController), same gating as every other write on this
     * entity. Checked at checkout before any credit-flagged payment is accepted
     * (OrderService.checkout) — a customer with this false can only pay with non-credit methods,
     * regardless of creditLimit.
     */
    private boolean creditEnabled = false;

    /**
     * The most this customer may ever owe at once (Task: "Set customer credit limit") —
     * enforced at checkout (Task: "Credit limit enforcement"; see
     * CustomerCreditService.assertWithinLimit). Defaults to zero, not null, mirroring
     * Product.costPrice's "default missing numeric to zero" convention — a credit-enabled
     * customer with no limit set yet simply can't buy anything on credit until an admin sets one
     * above zero, rather than the field being null and needing special-casing everywhere it's read.
     */
    private BigDecimal creditLimit = BigDecimal.ZERO;

    /**
     * Optional default payment term in days (Task: "Optional due date/payment terms") — when a
     * credit sale doesn't specify its own due date at checkout, OrderService.checkout computes
     * one as completedAt + this many days. Null (the default) means "no due date unless one is
     * given explicitly at checkout" — a credit sale for this customer never becomes "overdue" on
     * its own.
     */
    private Integer defaultCreditTermDays;

    /**
     * Marks the one customer the terminal pre-selects on a brand-new cart instead of leaving it
     * with no customer at all (Fourier's own request: "since we have a default customer
     * 'Walk-in Customer' it is better to set this customer as default selected in the terminal
     * ... instead of having null"). {@code DataSeeder} flags the "Walk-in Customer" row it seeds
     * with this true on a fresh install, and backfills it onto an existing store's own
     * identically-named customer on upgrade (see {@code DataSeeder.backfillWalkInCustomer}).
     * Nothing enforces at most one true row — the terminal just uses whichever one it finds
     * first — but nothing in this app sets it anywhere except that seed/backfill step, so in
     * practice there's exactly one. Not admin-editable from the Customer form; changing which
     * customer this is means editing the seed or the database directly.
     */
    private boolean walkIn = false;

    /**
     * Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment → Refund OR
     * → Customer Credit" — store credit the business owes this customer, built up only by {@code
     * CustomerCreditService.addCustomerCredit} (called from {@code
     * InvoiceService.cancelPaidInvoice}'s CUSTOMER_CREDIT branch). Defaults to zero, not null,
     * same "default missing numeric to zero" convention as {@link #creditLimit}. Deliberately the
     * opposite direction from this app's existing credit concept (creditEnabled/creditLimit, how
     * much the CUSTOMER may owe the STORE) — see CustomerCreditService's own "no floating balance"
     * class javadoc, which is about payments, not this. Purely a running, visible balance for now;
     * nothing in checkout or Record Payment draws it down automatically yet.
     */
    private BigDecimal creditBalance = BigDecimal.ZERO;
}
