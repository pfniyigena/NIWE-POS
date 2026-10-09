package com.posapp.recurringexpense;

import com.posapp.accounting.Account;
import com.posapp.accounting.CostCenter;
import com.posapp.accounting.PayeeType;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Admin &gt; Accounting &gt; Recurring expenses (Fourier: "Recurring expenses — things that
 * repeat every month without someone re-entering them each time"). A saved template, not an
 * expense itself — {@code RecurringExpenseTemplateService}'s scheduled sweep reads every {@link
 * #active} template whose {@link #nextRunDate} has arrived and, for each, calls {@code
 * com.posapp.accounting.ExpenseService#create(com.posapp.accounting.dto.ExpenseRequest, Long)}
 * with this template's own fields copied across — landing as a brand-new PENDING {@code Expense}
 * for someone to actually approve, exactly like a manually-entered one (Fourier's own "draft for
 * review" scoping decision: nothing here ever posts to Accounting by itself). {@code account}/
 * {@code payeeType}/{@code payeeId}/{@code payeeName}/{@code amount}/{@code paid}/{@code memo}/
 * {@code costCenter} mirror {@code Expense}'s own fields exactly, since they're copied onto a new
 * one verbatim at each run — see that class's own javadoc for what each means.
 *
 * <p>{@link #nextRunDate} starts at whatever date the admin first set as this template's first
 * billing date, and advances by {@link #frequency} after every run (see {@code
 * RecurringExpenseTemplateService#advance}) — never chosen directly by anyone once the template
 * exists. {@link #active} lets a template be paused without losing its history or its own
 * configuration (e.g. a lease that's on hold) — same "toggle, don't delete" convention as {@code
 * Supplier.active}/{@code Category.active}.
 */
@Getter
@Setter
@Entity
public class RecurringExpenseTemplate extends BaseEntity {

    /** A human label for this template, since "amount + payee" alone isn't enough to tell two recurring bills apart in a list — e.g. "Monthly shop rent". */
    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    @Enumerated(EnumType.STRING)
    private PayeeType payeeType = PayeeType.OTHER;

    private Long payeeId;

    @Column(nullable = false)
    private String payeeName;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Copied onto every generated Expense's own {@code paid} flag — see that class's javadoc. */
    private boolean paid = true;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    private CostCenter costCenter;

    @Enumerated(EnumType.STRING)
    private RecurrenceFrequency frequency = RecurrenceFrequency.MONTHLY;

    @Column(nullable = false)
    private LocalDate nextRunDate;

    private boolean active = true;

    /** Set only by RecurringExpenseTemplateService's scheduled sweep or its manual "run now" — null means this template has never generated an expense yet. */
    private Instant lastRunAt;
}
