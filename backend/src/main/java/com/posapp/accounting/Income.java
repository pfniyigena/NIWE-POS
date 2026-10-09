package com.posapp.accounting;

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
import java.time.LocalDate;

/**
 * Admin &gt; Accounting &gt; Incomes &gt; New — {@link Expense}'s mirror image, for money coming
 * in that isn't a POS/Sale Order sale: a rental income, a rebate, an interest credit, anything
 * this store earns outside the checkout flow. {@code account} must be a REVENUE-type {@link
 * Account} (enforced in {@code IncomeService#create}) other than the automatic {@code
 * SALES_REVENUE} the POS/Sale Order postings already own — an admin adds a dedicated Revenue
 * account (e.g. "Other Income") for these, same as an Expense's own category. {@code received}
 * mirrors {@code Expense#paid}: {@code true} (the default) debits {@code CASH} immediately;
 * {@code false} debits {@code ACCOUNTS_RECEIVABLE}, recording money owed to the store but not
 * yet collected (see {@code AccountingService#postIncome}).
 */
@Getter
@Setter
@Entity
public class Income extends BaseEntity {

    @Column(nullable = false)
    private LocalDate incomeDate;

    /** This income's category — must be a REVENUE-type account (enforced in IncomeService.create). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    @Enumerated(EnumType.STRING)
    private PayeeType payeeType = PayeeType.OTHER;

    /** Null when payeeType is OTHER, or when the admin typed a free-text payer without picking a specific record. */
    private Long payeeId;

    /** Always set — a snapshot of the payer's display name at entry time; see Expense.payeeName's own javadoc for why. */
    @Column(nullable = false)
    private String payeeName;

    @Column(nullable = false)
    private BigDecimal amount;

    /** True (the default) if this was received immediately into Cash; false if it's still owed to the store — see class javadoc. */
    private boolean received = true;

    @Column(columnDefinition = "TEXT")
    private String memo;

    /** Optional — see CostCenter's own javadoc. */
    @ManyToOne(fetch = FetchType.LAZY)
    private CostCenter costCenter;

    /**
     * Fourier: "How business branches can be introduced?" — the branch this income belongs to; chosen on the income, head office by default.
     * Null = head office, which is what a row created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
