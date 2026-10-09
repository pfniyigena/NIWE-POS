package com.posapp.credit;

import com.posapp.common.BaseEntity;
import com.posapp.invoice.Invoice;
import com.posapp.order.SaleOrder;
import com.posapp.salesorder.SalesOrder;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * How much of one {@link CustomerPayment} was applied to one specific outstanding order/invoice
 * (Task: "Payment allocation to orders") — a many-to-many join row between payments and whatever
 * they were allocated to, since a single payment can settle several outstanding things at once
 * (Task: "Multiple payments against one order" is the mirror image of this — one thing can also
 * be paid down across several separate payments over time, each leaving its own allocation row
 * here). An order/invoice's outstanding balance is always {@code total - sum(allocations against
 * it)} — see {@code CustomerCreditService.outstandingForOrder}/{@code outstandingForSalesOrder}/
 * {@code outstandingForInvoice} — never a separately-stored running total, so the two can't drift
 * apart.
 *
 * <p>{@code order}, {@link #salesOrder} and {@link #invoice} are an "exclusive arc": exactly one
 * of the three is ever set on a given row, the other two always null — {@link #order} for a POS
 * credit sale ({@link SaleOrder}), {@link #salesOrder} for a payment recorded directly against a
 * back-office {@link SalesOrder} (Task: "Add Sale order and Customer payment... so it shows up
 * alongside register sales in one place" — kept only for rows written before Fourier's own later
 * request below; see {@code CustomerCreditService.listOutstandingOrders}), and {@link #invoice}
 * for a payment recorded against an {@link Invoice} — generated from a Sales Order or created
 * standalone — which is what every *new* back-office payment targets (Fourier: "create invoice
 * related to the Sale Order and record payment on invoice ... someone can create invoice (with
 * invoice lines) without creating sales order"). Deliberately three nullable FK columns rather
 * than a new join table or a polymorphic association — the simplest, most additive change given
 * the schema has no migrations ({@code ddl-auto=update}). {@code CustomerCreditService
 * .recordPayment} is what enforces the exclusivity; this entity itself doesn't validate it.
 */
@Getter
@Setter
@Entity
public class CustomerPaymentAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private CustomerPayment payment;

    /** Set only when this allocation targets a POS credit sale. Null when {@link #salesOrder}/{@link #invoice} is set instead — see class javadoc. */
    @ManyToOne(fetch = FetchType.LAZY)
    private SaleOrder order;

    /** Set only on a legacy row recorded directly against a back-office Sales Order, before invoices carried payment — see class javadoc. Null on every new allocation. */
    @ManyToOne(fetch = FetchType.LAZY)
    private SalesOrder salesOrder;

    /** Set only when this allocation targets an Invoice (Sales-Order-derived or standalone) — see class javadoc. The target for every new back-office payment. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Invoice invoice;

    private BigDecimal amountApplied;
}
