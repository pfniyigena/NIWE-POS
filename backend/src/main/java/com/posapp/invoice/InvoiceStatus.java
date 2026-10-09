package com.posapp.invoice;

/**
 * An invoice's lifecycle (Fourier: "Build Quotation, Invoice, and the purchase-requisition
 * approval gate ... without touching warehousing"; Fourier later: "create invoice related to the
 * Sale Order and record payment on invoice ... someone can create invoice (with invoice lines)
 * without creating sales order"). One generated from a Sales Order (see {@code
 * InvoiceService.generate}) skips straight to {@code ISSUED} — it's already a snapshot of an
 * order that's been confirmed, nothing left to review. A manually-created, standalone invoice
 * (no Sales Order behind it) starts life as {@code DRAFT} instead — freely editable (add/remove
 * lines, change quantities/prices/customer) via {@code InvoiceService.updateDraft} — until {@code
 * InvoiceService.issue} moves it to {@code ISSUED}, which is the moment it becomes a real,
 * numbered, payable document (its {@code invoiceDate} is stamped then, not at draft-creation
 * time) and — for a standalone invoice only — posts its own revenue/receivable to Accounting (see
 * {@code AccountingService.postInvoiceIssued}'s javadoc for why a Sales-Order-derived invoice
 * never needs this). From {@code ISSUED}, the only further move is to {@code VOID} (e.g. to
 * correct a numbering mistake) — same terminal state as before this Draft stage existed.
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    VOID
}
