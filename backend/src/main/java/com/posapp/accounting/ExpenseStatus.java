package com.posapp.accounting;

/**
 * An {@link Expense}'s approval lifecycle (Fourier: "Approval workflow before posting") — mirrors
 * {@code com.posapp.inventory.StockAdjustmentStatus}'s maker-checker shape exactly:
 * {@code ExpenseService.create} only ever sets PENDING and never posts to Accounting; only
 * {@code ExpenseService#approve} — which moves the status to APPROVED — actually posts the
 * journal entry (see {@code AccountingService#postExpense}); {@code reject} moves to REJECTED
 * with no side effects at all, same as before any approval was ever recorded. VOIDED is a fourth,
 * later state that {@code StockAdjustmentStatus} has no equivalent of: an already-APPROVED (and
 * so already-posted) expense can still turn out to be wrong after the fact, and
 * {@code ExpenseService#voidExpense} reverses its posting with an equal-and-opposite journal
 * entry (see {@code AccountingService#postExpenseVoid}) rather than deleting the record — the
 * same "never erase a real posted event, reverse it instead" stance every other posted-then-
 * corrected document in this app takes (POS/Sale Order/Purchase returns, Invoice void).
 */
public enum ExpenseStatus {
    PENDING,
    APPROVED,
    REJECTED,
    VOIDED
}
