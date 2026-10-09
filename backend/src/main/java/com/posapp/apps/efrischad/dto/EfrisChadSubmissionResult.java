package com.posapp.apps.efrischad.dto;

/**
 * Outcome of one {@code EfrisChadSubmissionService#submit} call. Mirrors {@code
 * com.posapp.apps.efris.dto.EfrisSubmissionResult} (Rwanda's equivalent).
 * <p>
 * Task: "If the response is SUCCESS when \"response_code\"=\"P\" then parse the result of
 * sdc_data (split by ,) as follow: -Second value is sdc_id -Third value is MRC -Fourth is
 * sdc_receipt_number -Next is sdc_date -Next is signature_data -Next is internal_data". Fourier's
 * numbering is 1-indexed against the comma-separated {@code response_data.sdc_data} string (e.g.
 * {@code "6043139X,SCV000000001,CPF00000002,64/64 NV,2026-09-16 13:46:23,AKAF-...,A5A5-..."}), so
 * 0-indexed this is: [1]=sdc_id, [2]=mrc, [3]=sdc_receipt_number, [4]=sdc_date,
 * [5]=signature_data, [6]=internal_data. Index [0] (the tin_number, already known separately)
 * isn't named by Fourier's spec and is simply unused here. Unlike Rwanda's {@code
 * sdc_receipt_number} (a composite joined from three sub-values), Chad's is a single direct
 * position, so no joining helper is needed.
 * <p>
 * Two further follow-up tasks add {@code cisNumber}/{@code cisDate}, Chad's own equivalent of
 * Rwanda's CIS fields even though neither comes from splitting {@code sdc_data}: Task 1: "The
 * cis_data for sale_order should be the one send to json cis_date" — the fiscal {@code cisDate}
 * stored on the sale is exactly the {@code cis_date} string this same submission sent in its
 * request body (see {@code EfrisChadSubmissionService#submit}), not anything parsed back out of
 * the response. Task 2: "The cis_number is picked from tag invoice_number of json response" —
 * {@code cisNumber} comes from the response's {@code response_data.invoice_number}.
 */
public record EfrisChadSubmissionResult(
        boolean success,
        String errorMessage,
        String sdcId,
        String mrc,
        String sdcReceiptNumber,
        String sdcDate,
        String signatureData,
        String internalData,
        String cisNumber,
        String cisDate,
        String rawResponse
) {

    /**
     * @param sdcData   the response's {@code response_data.sdc_data} comma-separated string,
     *                  split per Fourier's exact spec (see class javadoc). {@code null}/short/
     *                  garbled input never throws — missing positions simply come back {@code
     *                  null}.
     * @param cisNumber the response's {@code response_data.invoice_number} (Task 2).
     * @param cisDate   the exact {@code cis_date} string this same request sent (Task 1) — not
     *                  parsed from the response at all.
     */
    public static EfrisChadSubmissionResult success(String sdcData, String cisNumber, String cisDate, String rawResponse) {
        String[] parts = splitLenient(sdcData, 7);
        return new EfrisChadSubmissionResult(
                true, null,
                parts[1], parts[2], parts[3], parts[4], parts[5], parts[6],
                cisNumber, cisDate,
                rawResponse
        );
    }

    /** No response body available yet (e.g. no EFRIS Chad URL configured, or the request itself failed to build/send). */
    public static EfrisChadSubmissionResult failure(String message) {
        return new EfrisChadSubmissionResult(false, message, null, null, null, null, null, null, null, null, null);
    }

    /** A response body was received (or is the literal text that failed to parse) — kept for review alongside the error. */
    public static EfrisChadSubmissionResult failure(String message, String rawResponse) {
        return new EfrisChadSubmissionResult(false, message, null, null, null, null, null, null, null, null, rawResponse);
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
