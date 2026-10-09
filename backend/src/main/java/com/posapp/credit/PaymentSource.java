package com.posapp.credit;

/**
 * Discriminates which underlying record a {@link com.posapp.credit.dto.CustomerPaymentDto} row
 * was built from, now that the Customer Payment history merges two different kinds of payment
 * (Fourier's own follow-up: "On Customer payment list no POS payment for cash sale"). {@code
 * SETTLEMENT} is an actual {@link CustomerPayment} — a later payment recorded against something
 * already outstanding (a confirmed Sales Order, or a POS credit sale) via the Record Payment
 * screen. {@code POS_SALE} is a {@code com.posapp.order.Payment} row tendered at checkout on a
 * completed {@code SaleOrder} — cash, card, or any other method paid in full at time of sale,
 * which never becomes a {@code CustomerPayment} because nothing about it was ever left
 * outstanding to settle later (see {@code CustomerCreditService}'s widened {@code
 * paymentHistory}/{@code paymentHistory(Long)} for where the two are merged).
 */
public enum PaymentSource {
    SETTLEMENT,
    POS_SALE
}
