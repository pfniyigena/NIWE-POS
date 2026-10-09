package com.posapp.recurringinvoice;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin &gt; Sales &gt; Recurring invoices (Fourier: "Add Recurring Invoice"). A saved template,
 * not an invoice itself — same "template vs. generated document" split {@code
 * com.posapp.recurringexpense.RecurringExpenseTemplate} already uses on the Accounting side — but
 * with real, full invoice line items (any catalog product, quantity and price) rather than a
 * single amount, and a customer rather than a payee, since this generates a real, numbered Sales
 * {@code com.posapp.invoice.Invoice} for a customer rather than an Accounting expense. Deliberately
 * a separate module from {@code com.posapp.subscription.Subscription} (Fourier, via
 * AskUserQuestion: "New standalone module ... for things like retainers or multi-item recurring
 * billing that don't fit a single 'plan' line") — a subscription bills one fixed plan at one
 * price; this bills whatever mix of catalog products the customer is actually being charged for
 * each cycle, same line-item shape a one-off standalone invoice already has.
 *
 * <p>{@code RecurringInvoiceTemplateService}'s scheduled sweep reads every {@link #active}
 * template whose {@link #nextRunDate} has arrived and, for each, calls {@code
 * com.posapp.invoice.InvoiceService#generateForRecurringInvoiceTemplate} followed immediately by
 * {@code InvoiceService#issue} — Fourier, via AskUserQuestion: "ISSUED immediately" — so the
 * generated invoice is already a real, posted document with no draft-review step, same treatment
 * subscription billing was just changed to give its own invoices.
 *
 * <p>{@link #nextRunDate} starts at whatever date the admin first set as this template's first
 * run date, and advances by {@link #frequency} after every run (see {@code
 * RecurringInvoiceTemplateService#generateFrom}) — never chosen directly by anyone once the
 * template exists, same convention as {@code RecurringExpenseTemplate.nextRunDate}/{@code
 * Subscription.nextBillingDate}. {@link #active} lets a template be paused without losing its
 * configuration or history (e.g. a retainer that's on hold) — same "toggle, don't delete"
 * convention as {@code RecurringExpenseTemplate.active}.
 */
@Getter
@Setter
@Entity
public class RecurringInvoiceTemplate extends BaseEntity {

    /** A human label for this template, since "customer + lines" alone isn't enough to tell two recurring invoices for the same customer apart — e.g. "Monthly retainer — website support". */
    @Column(nullable = false)
    private String name;

    /**
     * Plain denormalized fields rather than a {@code @ManyToOne Customer} — same convention
     * {@code com.posapp.subscription.Subscription}/{@code com.posapp.invoice.Invoice} already use
     * for their own customer link, so this module stays as decoupled from the Customer entity's
     * own internals as those already are; every generated invoice is created only through {@code
     * InvoiceService}'s own public API.
     */
    private Long customerId;

    private String customerName;

    @Enumerated(EnumType.STRING)
    private RecurrenceFrequency frequency = RecurrenceFrequency.MONTHLY;

    @Column(nullable = false)
    private LocalDate nextRunDate;

    /**
     * How many days after each generated invoice's own date it falls due — e.g. 30 for "net 30".
     * Null means every generated invoice is left with no due date at all, same as a standalone
     * invoice whose own {@code dueDate} was left blank.
     */
    private Integer dueInDays;

    /** Carried onto every generated invoice's own notes; a blank value falls back to a "Generated from recurring invoice template" note instead — see {@code RecurringInvoiceTemplateService#buildInvoiceRequest}. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Fourier: "Except POS other module can have global discount ... Either on line or global" —
     * same whole-document discount fields {@code com.posapp.invoice.Invoice} itself carries,
     * applied fresh (and re-validated against the store's current discount mode) on every invoice
     * this template generates, not just the one it was created against.
     */
    @Column(nullable = false)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private GlobalDiscountType discountType = GlobalDiscountType.PERCENT;

    private BigDecimal discountFixedAmount;

    private boolean active = true;

    /** Set only by RecurringInvoiceTemplateService's scheduled sweep or its manual "run now" — null means this template has never generated an invoice yet. */
    private Instant lastRunAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurringInvoiceTemplateLine> lines = new ArrayList<>();

    /**
     * Fourier: "How business branches can be introduced?" — the branch the invoices generated from this template are issued from.
     * Null = head office, which is what a row created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
