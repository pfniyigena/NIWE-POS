package com.posapp.inventory;

/**
 * A purchase requisition's lifecycle (Fourier: "Build Quotation, Invoice, and the
 * purchase-requisition approval gate ... without touching warehousing"). This is the internal
 * "can we buy this" approval step that sits in front of {@link PurchaseOrder} — deliberately
 * commits to nothing itself: no supplier, no pricing, no PO exists until an ADMIN approves it and
 * then converts it (see {@link PurchaseRequisitionService#convertToPurchaseOrder}), at which point
 * the supplier and each line's unit cost are supplied for the first time. DRAFT is freely editable
 * (add/remove lines, change quantities); submitting locks it to edits and puts it in front of an
 * admin. Only an APPROVED requisition can be converted; a REJECTED or CANCELLED one is simply
 * done, the same "no undoing a real-world event, no reviving a dead one" stance {@code
 * PurchaseOrderStatus}/{@code SalesOrderStatus} already take on their own terminal states.
 */
public enum PurchaseRequisitionStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CONVERTED,
    CANCELLED
}
