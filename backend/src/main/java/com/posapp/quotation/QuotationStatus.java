package com.posapp.quotation;

/**
 * A quotation's lifecycle (Fourier: "Build Quotation, Invoice, and the purchase-requisition
 * approval gate ... without touching warehousing"). Deliberately lighter than {@code
 * com.posapp.salesorder.SalesOrderStatus}: a quotation is a non-binding price offer, so nothing
 * here ever touches stock, credit limits, or amounts owed — see {@code QuotationService}'s class
 * javadoc. DRAFT is freely editable; SENT is what the customer sees; ACCEPTED/REJECTED/EXPIRED are
 * all "the customer has responded (or the offer lapsed) and this quote is done" — only ACCEPTED
 * can still move, via {@link QuotationService#convertToSalesOrder}, to CONVERTED.
 */
public enum QuotationStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CONVERTED
}
