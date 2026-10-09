package com.posapp.credit;

/**
 * Discriminates which kind of order a {@link CustomerPaymentAllocation} (or an outstanding-order
 * row derived from one) targets — see that entity's "exclusive arc" javadoc, now three-way.
 * {@code POS_SALE} is a register checkout ({@code com.posapp.order.SaleOrder}). {@code
 * SALES_ORDER} is a back-office order taken outside the register ({@code
 * com.posapp.salesorder.SalesOrder}) — kept only for payments recorded before Fourier's own later
 * request ("create invoice related to the Sale Order and record payment on invoice"); {@code
 * CustomerCreditService.listOutstandingOrders} no longer offers a Sales Order directly as a *new*
 * payment target, so no allocation created from here on ever sets this. {@code INVOICE} is the
 * replacement — a {@code com.posapp.invoice.Invoice}, whether generated from a Sales Order or
 * created standalone; every new back-office payment is recorded against one of these instead.
 */
public enum OrderType {
    POS_SALE,
    SALES_ORDER,
    INVOICE
}
