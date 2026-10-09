package com.posapp.salesorder;

/**
 * A Sales Order's lifecycle ("Add Sale order... So that we can have POS & Sales") — deliberately
 * mirrors {@code com.posapp.inventory.PurchaseOrderStatus} on the sales side of the ledger. DRAFT
 * is freely editable (add/remove lines, change quantities/prices); everything from CONFIRMED
 * onward is locked to edits — only {@link SalesOrderService#fulfillLines} and {@link
 * SalesOrderService#cancel} can still move it. Confirming is the moment the customer's full order
 * amount becomes something they owe (see {@code SalesOrderService}'s class javadoc), but — unlike
 * a POS credit sale at checkout — confirming a Sales Order does NOT require {@code
 * Customer.creditEnabled} or check {@code Customer.creditLimit} (Task: "The rules of enabling
 * credit on customer should only be required in POS Module not in Sale Module"); any customer can
 * have a Sales Order confirmed against them. PARTIALLY_FULFILLED/FULFILLED are set automatically
 * by {@code fulfillLines} from how much of each line has actually shipped so far, never chosen
 * directly by the admin/sales rep.
 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_FULFILLED,
    FULFILLED,
    CANCELLED
}
