package com.posapp.inventory;

import com.posapp.bill.Bill;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line of a {@link SupplierPayment}: the portion of that payment ({@code amountApplied})
 * applied against a specific outstanding document — the accounts-payable mirror of {@code
 * com.posapp.credit.CustomerPaymentAllocation}. A payment's allocations must sum to exactly its
 * {@code amount}, and no single allocation may exceed what's still owed on its target at the time
 * the payment is recorded (see {@code SupplierPaymentService.recordPayment}/{@code
 * recordBillPayment}).
 *
 * <p>Fourier: "The Purchase order should flow same workflow as Sale order (with Bills ... equivalent
 * with Invoices)" — {@link #purchaseOrder} and {@link #bill} are an "exclusive arc," same shape as
 * {@code CustomerPaymentAllocation}'s own {@code order}/{@code salesOrder}/{@code invoice}: exactly
 * one of the two is ever set on a given row. {@link #purchaseOrder} is every allocation recorded
 * before this request, kept working unchanged, and still the target for a purchase order that owes
 * money before it has a {@link Bill} of its own; {@link #bill} is what every *new* payment targets
 * once one exists — see {@code PayableType}'s own javadoc. Deliberately an additive nullable FK
 * column rather than a schema migration — this app has none ({@code ddl-auto=update}).
 */
@Getter
@Setter
@Entity
public class SupplierPaymentAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SupplierPayment payment;

    /** Set only when this allocation targets a Purchase Order directly. Null when {@link #bill} is set instead — see class javadoc. */
    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseOrder purchaseOrder;

    /** Set only when this allocation targets a Bill — the target for every new back-office supplier payment once one exists. See class javadoc. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Bill bill;

    private BigDecimal amountApplied;
}
