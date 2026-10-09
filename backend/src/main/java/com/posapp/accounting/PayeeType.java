package com.posapp.accounting;

/**
 * Who the other side of an {@link Expense} or {@link Income} entry is — a lightweight tag used
 * for both the entry form's own lookup (which existing record to search) and the "Expenses/Incomes
 * by Vendor/Staff/Client" segmented reports ({@code AccountingReportService.expensesGrouped}/
 * {@code incomesGrouped}). Deliberately reuses the same four values for both directions rather
 * than having separate enums — an expense's {@code VENDOR} is who the store paid ({@link
 * com.posapp.inventory.Supplier}), an income's {@code VENDOR} is unusual but not impossible (e.g.
 * a supplier rebate); {@code STAFF} ({@link com.posapp.user.AppUser}) covers a staff
 * reimbursement expense or a staff cash-advance repayment income; {@code CLIENT} ({@link
 * com.posapp.customer.Customer}) is who paid the store (the common case for Income) but can also
 * be who an expense reimburses (e.g. a customer refund recorded outside the POS return flow).
 * {@code OTHER} means {@code payeeId} is null and only the free-text {@code payeeName} snapshot
 * identifies who this was.
 */
public enum PayeeType {
    VENDOR,
    STAFF,
    CLIENT,
    OTHER
}
