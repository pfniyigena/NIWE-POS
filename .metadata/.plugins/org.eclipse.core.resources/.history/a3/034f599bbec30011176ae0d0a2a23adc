package com.posapp.invoice;

/**
 * Fourier, on invoice INV-1790950561007 ("Cannot void invoice ... it already has a payment
 * recorded against it"): "VOID INVOICE → Only allowed when there is NO payment. CANCEL/CREDIT
 * PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit." {@link
 * InvoiceService#voidInvoice} stays exactly as it was — still refused once any payment exists
 * (see its own javadoc) — this enum is only for the new, separate path that handles that case:
 * {@link InvoiceService#cancelPaidInvoice} requires one of these two choices for what happens to
 * the amount already collected.
 */
public enum PaymentDisposition {
    /**
     * The amount already paid is handed back to the customer right now, through a real payment
     * method (cash, card, mobile money, ...) — same "pick an active payment-method code" shape as
     * {@code ReturnsService}'s own POS-return refund, and posts out of {@code AccountService.CASH}
     * the same way.
     */
    REFUND,
    /**
     * The amount already paid stays with the store but is now owed back to the customer as usable
     * credit — added to {@code Customer.creditBalance} (see {@code
     * CustomerCreditService#addCustomerCredit}) and posted to the new {@code
     * AccountService.CUSTOMER_CREDIT_PAYABLE} liability account instead of Cash leaving.
     */
    CUSTOMER_CREDIT
}
