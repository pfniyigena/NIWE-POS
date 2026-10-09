package com.posapp.user;

/**
 * Fourier: "The biggest one is authorization granularity: Role is just ADMIN and CASHIER —
 * nothing in between" — a flat per-user grant list, additive on top of {@link Role}. Holding one
 * of these never takes anything away from what a user's {@link Role} already allows; it only ever
 * adds a specific ADMIN-tier power to an otherwise-CASHIER account (see
 * {@code com.posapp.security.AppUserPrincipal#getAuthorities()}, which emits one
 * {@code PERM_<name>} authority per granted permission alongside the existing
 * {@code ROLE_<role>} one). Every existing ADMIN-only check stays exactly {@code
 * hasRole('ADMIN')} — these are additive {@code hasRole('ADMIN') or hasAuthority('PERM_<name>')}
 * checks layered on top, never a replacement.
 */
public enum Permission {
    /** Confirm/reject a PENDING {@code RestaurantWastage} entry — see RestaurantWastageController. */
    CONFIRM_RESTAURANT_WASTAGE,
    /** Confirm/reject a PENDING {@code StockAdjustment} — see StockAdjustmentController. */
    APPROVE_STOCK_ADJUSTMENT,
    /** Approve/reject a PENDING_APPROVAL {@code PurchaseRequisition} — see PurchaseRequisitionController. */
    APPROVE_PURCHASE_REQUISITION,
    /** Apply a discount at all, and exceed {@code StoreSettings.maxDiscountPercent} — see OrderService.validateDiscount. */
    OVERRIDE_DISCOUNT_CAP,
    /** Approve/reject a PENDING {@code com.posapp.accounting.Expense}, and void an already-APPROVED one — see ExpenseController. */
    APPROVE_EXPENSE,
    /** Confirm/reject a PENDING {@code com.posapp.warehouse.StockTransfer} — see StockTransferController. */
    APPROVE_STOCK_TRANSFER
}
