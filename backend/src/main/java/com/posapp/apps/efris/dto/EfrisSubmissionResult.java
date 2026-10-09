package com.posapp.apps.efris.dto;

/**
 * Outcome of one {@link EfrisSubmissionService#submit} call — either the eight fiscal fields
 * {@code SaleOrder} already has columns for, or a plain-text error. Never throws: a network
 * failure, a non-200 response, a JSON-RPC-level {@code error}, or a non-blank {@code result.error}
 * all become a {@link #failure} so {@code OrderService} can apply the "sale still completes,
 * flagged for review" policy uniformly. Also carries {@link #rawResponse}, the raw EFRIS response
 * body — Fourier's own request, "also record Efris response" — captured whenever an actual HTTP
 * response was received (null for the earlier failures, e.g. no URL configured or the request
 * couldn't even be sent).
 * <p>
 * {@link #success(String, String, String)} parses EFRIS's two comma-separated response strings
 * positionally — Fourier's own exact split spec, replacing an earlier best-effort guess from a
 * single worked example:
 * <pre>
 * sdc_data (7 comma-separated values), e.g.
 * "SDC011001811,301271,328875,NS,16/09/2026 07:38:15,LQQI-NSBN-67MM-OAJE,ID67-EP57-VKB2-5SFL-2SNK-MH4O-6Q"
 *   [0] sdcId
 *   [1..3] sdcReceiptNumber — joined as "{1}/{2} {3}", e.g. "301271/328875 NS"
 *   [4] sdcDate — a combined "date time" string (e.g. "16/09/2026 07:38:15"); the receipt
 *       printout splits it on the first space to show DATE and TIME separately
 *   [5] signatureData
 *   [6] internalData
 *
 * cis_data (3 comma-separated values), e.g. "16/09/2026 07:38:15,328876,SPLTD000073"
 *   [0] cisDate — same combined "date time" shape as sdcDate
 *   [1] cisNumber
 *   [2] mrcData
 * </pre>
 * If a real submission's fields land in the wrong {@code SaleOrder} columns, this is the one
 * place to fix the split/index logic.
 */
public record EfrisSubmissionResult(
        boolean success,
        String errorMessage,
        String sdcId,
        String sdcDate,
        String sdcReceiptNumber,
        String internalData,
        String signatureData,
        String cisNumber,
        String cisDate,
        String mrcData,
        String rawResponse
) {

    public static EfrisSubmissionResult success(String sdcData, String cisData, String rawResponse) {
        String[] sdc = splitLenient(sdcData, 7);
        String[] cis = splitLenient(cisData, 3);
        return new EfrisSubmissionResult(
                true, null,
                sdc[0], sdc[4], joinReceiptNumber(sdc[1], sdc[2], sdc[3]), sdc[6], sdc[5],
                cis[1], cis[0], cis[2],
                rawResponse
        );
    }

    /** No response body available yet (e.g. no EFRIS URL configured, or the request itself failed to build/send). */
    public static EfrisSubmissionResult failure(String message) {
        return new EfrisSubmissionResult(false, message, null, null, null, null, null, null, null, null, null);
    }

    /** A response body was received (or is the literal text that failed to parse) — kept for review alongside the error. */
    public static EfrisSubmissionResult failure(String message, String rawResponse) {
        return new EfrisSubmissionResult(false, message, null, null, null, null, null, null, null, null, rawResponse);
    }

    /**
     * {@code sdc_data}'s 2nd/3rd/4th comma-separated values, joined into the one printed
     * "SDC RECEIPT NUMBER" (e.g. {@code "301271/328875 NS"}) — Fourier's own format, {@code
     * "{v1}/{v2} {v3}"}. Null (not a string of slashes/spaces around blanks) if all three parts
     * are missing, so a short/garbled response degrades to "nothing to show" like every other
     * field here rather than a confusing partial punctuation-only string.
     */
    private static String joinReceiptNumber(String v1, String v2, String v3) {
        if (v1 == null && v2 == null && v3 == null) {
            return null;
        }
        return (v1 == null ? "" : v1) + "/" + (v2 == null ? "" : v2) + " " + (v3 == null ? "" : v3);
    }

    /** Splits on commas and pads with nulls so a shorter-than-expected response never throws. */
    private static String[] splitLenient(String raw, int expectedParts) {
        String[] result = new String[expectedParts];
        if (raw == null) {
            return result;
        }
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < expectedParts && i < parts.length; i++) {
            result[i] = parts[i].trim();
        }
        return result;
    }
}
