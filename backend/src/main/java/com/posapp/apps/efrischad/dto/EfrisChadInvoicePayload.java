package com.posapp.apps.efrischad.dto;

import java.time.Instant;
import java.util.List;

/**
 * A completed sale, reduced to exactly what EFRIS Chad's HTTP(S)/basic-auth submission needs — a
 * plain record with no reference to {@code SaleOrder}/{@code Customer}/{@code Register}, built by
 * {@code OrderService} itself (see the {@code order} → {@code apps.efrischad} one-directional
 * dependency convention {@code EfrisChadConnection}/{@code com.posapp.apps.efris.dto
 * .EfrisConnection} already establish for Rwanda). Mirrors {@code com.posapp.apps.efris.dto
 * .EfrisInvoicePayload} (Rwanda's equivalent) with Chad's own distinct field set.
 *
 * <p>Task: "if EFRIS Chad is set then call configured with the shared sample request" — this pass
 * covers a completed SALE only, same incremental order Rwanda's own submission shipped in (returns
 * came later, once Fourier gave the exact {@code transaction_type} to send for one). Chad's sample
 * shows a single {@code transaction_type} value ({@code "V"}) with no return-equivalent code given
 * yet, so {@code EfrisChadSubmissionService} always sends that one literal and this record carries
 * no {@code transactionType}/{@code erpCodeRef} field the way Rwanda's payload does — a return
 * submission is a deliberate follow-up once Fourier shares Chad's own return transaction type.</p>
 *
 * @param companyName        EFRIS Chad's {@code registered_name} — reuses {@code
 *                           StoreSettings.businessName}, same source as Rwanda's own {@code
 *                           company_tin} pulling from {@code StoreSettings.taxId}.
 * @param companyTin         EFRIS Chad's {@code tin_number} — reuses {@code StoreSettings.taxId}.
 * @param externalReference EFRIS Chad's {@code external_reference} — reuses {@code
 *                           SaleOrder.invoiceNumber} unchanged; it's already generated as
 *                           {@code "POS-" + System.currentTimeMillis()} (see {@code
 *                           OrderService.checkout}), the exact same shape as the sample's own
 *                           {@code "POS-1789562530534"}.
 * @param paymentMode        EFRIS Chad's {@code payment_mode} — Fourier's own choice when asked:
 *                           each distinct {@code Payment.method} code, upper-cased and prefixed
 *                           {@code "PAYMENT_METHOD_"} (e.g. {@code "CASH"} &rarr;
 *                           {@code "PAYMENT_METHOD_CASH"}, matching the sample exactly), joined
 *                           with a comma for a split-tender sale — same joining convention as
 *                           Rwanda's own {@code payment_mode}. Null for a sale with no payments.
 * @param orderDate          EFRIS Chad's {@code cis_date} — Fourier's own mapping: "Order date in
 *                           this format '2026-01-04 10:03:41'", i.e. {@code SaleOrder.completedAt}
 *                           (when the sale itself completed), not whenever the HTTP submission
 *                           happens to run — a retry minutes or hours later must still report the
 *                           original sale time, not the retry time. {@code
 *                           EfrisChadSubmissionService} formats this into the exact
 *                           {@code "yyyy-MM-dd HH:mm:ss"} string the sample shows.
 */
public record EfrisChadInvoicePayload(
        String companyName,
        String companyTin,
        String clientName,
        String clientTin,
        String clientPhone,
        String externalReference,
        String paymentMode,
        Instant orderDate,
        List<EfrisChadInvoiceLine> lines
) {
}
