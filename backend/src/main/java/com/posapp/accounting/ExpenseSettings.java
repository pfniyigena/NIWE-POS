package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One row: approval and budget rules and bill-due defaults for Expenses. */
@Getter
@Setter
@Entity
public class ExpenseSettings extends BaseEntity {
    /** Expenses above this amount need an administrator or someone with the large-expense permission; 0 = no limit. */
    private BigDecimal approvalLimit = BigDecimal.ZERO;
    /** Nobody can approve, reject or void an expense they entered themselves. */
    private boolean blockSelfApproval = false;
    /** An expense that would push its category or cost center over the monthly budget cannot be approved (an administrator may override). */
    private boolean blockOverBudget = false;
    /** Due date given to an unpaid bill when none is entered: expense date plus this many days. */
    private int defaultDueDays = 30;
    /** Bills due within this many days show as "due soon". */
    private int dueSoonDays = 7;
}
