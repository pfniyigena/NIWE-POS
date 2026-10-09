package com.posapp.apps.efrischad.dto;

import java.math.BigDecimal;

/**
 * One sale line as EFRIS Chad's {@code details_request[]} expects it (Fourier's own sample
 * payload). Mirrors {@code com.posapp.apps.efris.dto.EfrisInvoiceLine} (Rwanda's equivalent), with
 * Chad's own distinct field set. {@code taxAmount}/{@code totalAmount}/{@code grossAmount} are
 * deliberately not part of this record, same reasoning as Rwanda's line: the sample sends those as
 * literal {@code 0} and lets EFRIS compute them server-side from {@code unitPrice}/{@code
 * quantity}/{@code taxRatePercent}, so {@code EfrisChadSubmissionService} hardcodes those three to
 * zero rather than this app trying to reproduce EFRIS's own tax math.
 *
 * @param classificationCode EFRIS Chad's {@code classification_code} — reuses {@code
 *                            Product.unspCode}/{@code OrderLine.unspCode}, the same UNSPSC-style
 *                            commodity code EFRIS Rwanda's {@code unsp_code} already uses; it's the
 *                            same kind of classification code under a different JSON key, not a
 *                            second field to maintain.
 * @param service             Task: "if EFRIS Chad is set then call configured with the shared
 *                            sample request" — Fourier's own choice when asked: reuses {@code
 *                            Product.service}/{@code OrderLine.service} (goods by default) to
 *                            decide {@code item_nature} ({@code ITEM_NATURE_GOODS}/{@code
 *                            ITEM_NATURE_SERVICE}); {@code item_type} itself is always the sample's
 *                            own {@code "ITEM_TYPE_FINISHED_PRODUCT"} literal — the sample's only
 *                            example doesn't show it varying by goods vs. service, only {@code
 *                            item_nature} does.
 * @param taxCode             EFRIS Chad's {@code tax_code} — reuses {@code OrderLine.taxCode}, the
 *                             same store-configured tax-category code EFRIS Rwanda's {@code
 *                             tax_label} already uses, falling back to the sample's own {@code
 *                             "TN"} when a line has no tax code at all (mirrors {@code
 *                             OrderService.DEFAULT_EFRIS_TAX_CODE}'s "B" fallback for Rwanda, just
 *                             Chad's own default literal).
 * @param sequence            EFRIS Chad's {@code item_sequence} — this line's 1-based position in
 *                            the sale, computed by {@code EfrisChadSubmissionService}, not carried
 *                            on the line itself.
 */
public record EfrisChadInvoiceLine(
        String itemCode,
        String itemName,
        boolean service,
        BigDecimal unitPrice,
        int quantity,
        String classificationCode,
        BigDecimal taxRatePercent,
        String taxCode,
        BigDecimal discountPercent,
        int sequence
) {
}
