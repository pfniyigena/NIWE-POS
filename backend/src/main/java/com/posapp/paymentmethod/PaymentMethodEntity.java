package com.posapp.paymentmethod;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * A configurable payment/refund method (was a hardcoded enum). Stored as a plain string "code"
 * on Payment/SalesReturn rather than a foreign key, so renaming a method's display name later
 * never touches historical rows, and a deactivated method's code still reads back correctly on
 * old receipts. `cash` marks which methods count toward till cash reconciliation (RegisterService
 * expected-cash calc, OrderService's cash/non-cash split) — exactly one or more methods should be
 * flagged cash (CASH, by default).
 */
@Getter
@Setter
@Entity
public class PaymentMethodEntity extends BaseEntity {

    /** Stable identifier used on Payment/SalesReturn rows and in the checkout API — never shown to the user. */
    @Column(nullable = false, unique = true)
    private String code;

    /** Display label shown in the checkout/refund dropdowns. */
    @Column(nullable = false)
    private String name;

    private boolean active = true;

    /** True if payments with this method should be treated as cash-in-drawer for till reconciliation. */
    private boolean cash = false;

    /**
     * True if payments with this method represent selling on store credit rather than an
     * immediate tender (Task: "Enable credit for customer" family). Mirrors `cash` exactly:
     * exactly one method should normally be flagged credit (CREDIT/"Store Credit", seeded by
     * DataSeeder). OrderService.checkout sums payments flagged credit into SaleOrder.creditAmount
     * and runs credit-limit enforcement on that sum instead of treating it as tendered cash.
     */
    private boolean credit = false;

    /**
     * Display order in the checkout/refund method list (Task: "Make payment method order dynamic
     * so I can set the first, second on checkout screen") — lower shows first. Ties (including
     * every method before this pass, which all default to 0) fall back to alphabetical `name`,
     * so nothing looks unordered until an admin actually sets distinct values. Purely a display
     * concern — never read by checkout/return business logic, unlike `cash`/`credit`.
     */
    private int sortOrder = 0;
}
