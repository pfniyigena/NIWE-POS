package com.posapp.apps.efris.dto;

import java.math.BigDecimal;

/**
 * One sale line as EFRIS's {@code api.invoice/create_api} expects it (Fourier's own sample
 * payload's {@code sales[]} entries). {@code taxAmount}/{@code discountAmount}/{@code
 * grossAmount} are deliberately not part of this record — the sample sends those as literal
 * {@code 0} and lets EFRIS compute them server-side from {@code unitPrice}/{@code quantity}/
 * {@code discountPercent}, so {@link EfrisSubmissionService} hardcodes those three to zero rather
 * than this app trying to reproduce EFRIS's own tax math.
 *
 * @param taxLabel named after the JSON key it becomes ({@code tax_label}), same convention as
 *                 {@code erpCode}/{@code erpCodeRef} on {@link EfrisInvoicePayload}. Carries
 *                 {@code OrderLine.taxCode} — this app's own stable tax-rate identifier (e.g.
 *                 {@code "STD"}), snapshotted from {@code TaxRate.code} — falling back to
 *                 {@code "B"} (RRA's standard-rate category letter) when a line has no tax
 *                 code at all, since EFRIS requires this field on every line. The separate
 *                 admin-entered EFRIS-category-letter field this used to read instead
 *                 ({@code TaxRate.taxLabel}/{@code OrderLine.taxLabel}) has since been removed
 *                 from both entities entirely — see {@code OrderService.DEFAULT_EFRIS_TAX_CODE}.
 */
public record EfrisInvoiceLine(
        String productName,
        String productCode,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal taxRatePercent,
        String taxLabel,
        String unspCode,
        BigDecimal discountPercent
) {
}
