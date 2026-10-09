package com.posapp.apps.efris.dto;

import java.util.List;

/**
 * A completed sale or return, reduced to exactly what EFRIS's {@code api.invoice/create_api}
 * needs — a plain record with no reference to {@code SaleOrder}/{@code SalesReturn}/{@code
 * Customer}/{@code Register}, built by {@code OrderService}/{@code ReturnsService} themselves (see
 * the {@code order}/{@code returns} → {@code apps.efris} one-directional dependency convention).
 * {@code invoiceType} isn't a field here: every submission this app makes is a normal invoice, so
 * {@link EfrisSubmissionService} sends the sample's own literal {@code "N"} rather than this app
 * inventing other values it has no use for yet. {@code transactionType} *is* a field, unlike
 * before — Fourier's own follow-up ("The return will also integrate with EFRIS ... instead of
 * sending 'transaction_type':'S' send 'transaction_type':'R'") is the one place a submission needs
 * to vary this, so it moved from a hardcoded literal in {@link EfrisSubmissionService} to here.
 *
 * @param transactionType EFRIS's {@code transaction_type} — {@code "S"} (sale) for a completed
 *                        sale, {@code "R"} (return) for a processed return. No other value is
 *                        ever sent by this app.
 * @param erpCodeRef      EFRIS's {@code erp_code_ref} — {@code "0"} for an ordinary sale (nothing
 *                        to reference), or the *original* sale's own {@code erpCode} (its
 *                        {@code SaleOrder.invoiceNumber}) for a return, so EFRIS can tie the
 *                        return back to the receipt it's reversing. See {@code
 *                        ReturnsService.buildEfrisPayload}.
 * @param paymentMode this sale's payment method code(s) — the plain {@code Payment.method} code
 *                    this app already uses (e.g. {@code "CASH"}, {@code "CARD"}), not an
 *                    EFRIS-defined payment-mode enum (none was specified). A split-tender sale
 *                    with more than one distinct method joins them with a comma (e.g.
 *                    {@code "CASH,CARD"}) — EFRIS's own sample sends this as a single string
 *                    field, not an array, so there's nowhere else to put a second method. Null
 *                    for a sale with no payments at all, which shouldn't happen for a completed
 *                    sale but is handled the same "send null rather than guess" way every other
 *                    optional field on this record already is. A return reuses the *original*
 *                    sale's own payment mode (nothing new was tendered at return time) — see
 *                    {@code ReturnsService.buildEfrisPayload}.
 */
public record EfrisInvoicePayload(
        String transactionType,
        String companyTin,
        String clientTin,
        String clientName,
        String clientPhone,
        String clientAddress,
        String erpCode,
        String erpCodeRef,
        String paymentMode,
        List<EfrisInvoiceLine> lines
) {
}
