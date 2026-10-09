package com.posapp.apps.efrischad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posapp.apps.efrischad.dto.EfrisChadConnection;
import com.posapp.apps.efrischad.dto.EfrisChadInvoiceLine;
import com.posapp.apps.efrischad.dto.EfrisChadInvoicePayload;
import com.posapp.apps.efrischad.dto.EfrisChadSubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Submits one completed sale to EFRIS Chad over the plain HTTP(S)/basic-authentication protocol
 * Fourier's own sample used — a single JSON POST, no JSON-RPC envelope (contrast {@code
 * com.posapp.apps.efris.EfrisSubmissionService}, Rwanda's own submission over Odoo's {@code
 * /jsonrpc} convention). Task: "if EFRIS Chad is set then call configured with the shared sample
 * request." Deliberately has zero reference to {@code SaleOrder}/{@code Register}/{@code
 * Customer}: everything it needs arrives as {@link EfrisChadConnection}/{@link
 * EfrisChadInvoicePayload}, plain records {@code OrderService} builds from its own entities, same
 * one-directional dependency convention Rwanda's own submission service already follows.
 * <p>
 * Uses the JDK's built-in {@link HttpClient}, same as Rwanda's submission service. Never throws
 * outward: any failure — network, non-200, or a {@code response_code} other than {@code "P"} —
 * comes back as an {@link EfrisChadSubmissionResult#failure} so a down or misbehaving EFRIS Chad
 * endpoint never blocks a checkout, the same "sale still completes, flagged for review" policy
 * Rwanda's submission follows (enforced by {@code OrderService}, the only caller).
 * <p>
 * Every submission logs its outbound request and inbound response at {@code INFO}, tagged with the
 * order's own reference ({@code external_reference}) so a real submission can be traced end to end.
 * The HTTP Basic Authentication credentials go in the {@code Authorization} header, never the JSON
 * body, and that header is never logged (only its presence is), so a terminal's EFRIS Chad password
 * is never written to disk in plain text just because a sale happened to be rung.
 */
@Service
public class EfrisChadSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(EfrisChadSubmissionService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final DateTimeFormatter CIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Every sale this app submits to EFRIS Chad is a normal (not credit-note/return) invoice —
     * Fourier's sample sends {@code "N"}, same convention as Rwanda's own hardcoded {@code
     * invoice_type}. No other value is ever sent by this app.
     */
    private static final String INVOICE_TYPE = "N";

    /**
     * Fourier's sample's own {@code transaction_type} literal — the only value his sample shows,
     * with no documented alternative for a return yet. See {@link EfrisChadInvoicePayload}'s
     * javadoc for why returns aren't submitted to EFRIS Chad yet.
     */
    private static final String TRANSACTION_TYPE = "V";

    /**
     * Every invoice line this app submits is a finished product/service line — Fourier's sample's
     * own {@code item_type} literal, which doesn't vary between the sample's goods/service cases
     * (only {@code item_nature} does — see {@link EfrisChadInvoiceLine#service()}'s javadoc).
     */
    private static final String ITEM_TYPE = "ITEM_TYPE_FINISHED_PRODUCT";

    /** Fourier's sample's own {@code quantity_code} literal — no per-product unit-of-measure exists in this app to derive it from. */
    private static final String QUANTITY_CODE = "NB";

    /** Fourier's sample's own {@code country_code} literal (Chad's ISO3 code) — fixed for this integration, not per-sale data. */
    private static final String COUNTRY_CODE = "TCD";

    /**
     * Fourier's sample's own {@code tax_code} literal — the fallback used when a line has no
     * {@code OrderLine.taxCode} snapshot at all, mirroring {@code
     * OrderService.DEFAULT_EFRIS_TAX_CODE}'s "B" fallback for Rwanda (Chad's own default literal).
     */
    private static final String DEFAULT_TAX_CODE = "TN";

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EfrisChadSubmissionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public EfrisChadSubmissionResult submit(EfrisChadConnection connection, EfrisChadInvoicePayload payload) {
        String reference = payload != null ? payload.externalReference() : null;
        if (connection == null || connection.url() == null || connection.url().isBlank()) {
            return EfrisChadSubmissionResult.failure("No EFRIS Chad URL configured for this terminal");
        }
        // Task 1: "The cis_data for sale_order should be the one send to json cis_date" — computed
        // once here so the exact same string both goes into the outbound request body and (on
        // success) comes back out in the EfrisChadSubmissionResult, rather than reformatting
        // payload.orderDate() a second time and risking the two ever drifting apart.
        String cisDate = formatOrderDate(payload.orderDate());
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(buildRequestTree(connection, payload, cisDate));
        } catch (Exception e) {
            log.warn("Failed to build EFRIS Chad request body for sale {}", reference, e);
            return EfrisChadSubmissionResult.failure("Failed to build EFRIS Chad request: " + e.getMessage());
        }

        log.info("EFRIS Chad request for sale {} -> {}: {}", reference, connection.url(), requestBody);

        HttpRequest httpRequest;
        try {
            String credentials = (connection.basicAuthUsername() == null ? "" : connection.basicAuthUsername())
                    + ":" + (connection.basicPassword() == null ? "" : connection.basicPassword());
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(connection.url()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
        } catch (IllegalArgumentException e) {
            return EfrisChadSubmissionResult.failure("Invalid EFRIS Chad URL: " + connection.url());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            log.warn("EFRIS Chad submission failed for sale {} at {}", reference, connection.url(), e);
            return EfrisChadSubmissionResult.failure("Could not reach EFRIS Chad: " + e.getMessage());
        }

        log.info("EFRIS Chad response for sale {} <- {} (HTTP {}): {}",
                reference, connection.url(), response.statusCode(), response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return EfrisChadSubmissionResult.failure("EFRIS Chad returned HTTP " + response.statusCode(), response.body());
        }

        try {
            return parseResponse(response.body(), cisDate);
        } catch (Exception e) {
            log.warn("Failed to parse EFRIS Chad response for sale {}: {}", reference, response.body(), e);
            return EfrisChadSubmissionResult.failure("Could not parse EFRIS Chad response: " + e.getMessage(), response.body());
        }
    }

    private ObjectNode buildRequestTree(EfrisChadConnection connection, EfrisChadInvoicePayload payload, String cisDate) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("registered_name", payload.companyName());
        request.put("tin_number", payload.companyTin());
        request.put("sdc_number", connection.sdcNumber());
        request.put("branch_code", connection.branchCode());
        request.put("mrc", connection.mrc());
        request.put("invoice_type", INVOICE_TYPE);
        request.put("transaction_type", TRANSACTION_TYPE);
        request.put("cis_date", cisDate);
        request.put("external_reference", payload.externalReference());
        request.put("client_name", payload.clientName());
        request.put("integration_key", connection.integrationKey());
        request.put("client_tin", payload.clientTin());
        request.put("client_phone", payload.clientPhone());
        if (payload.paymentMode() != null && !payload.paymentMode().isBlank()) {
            request.put("payment_mode", payload.paymentMode());
        } else {
            request.putNull("payment_mode");
        }

        ArrayNode details = request.putArray("details_request");
        for (EfrisChadInvoiceLine line : payload.lines()) {
            ObjectNode detail = details.addObject();
            detail.put("item_code", line.itemCode());
            detail.put("item_name", line.itemName());
            detail.put("item_type", ITEM_TYPE);
            detail.put("item_nature", line.service() ? "ITEM_NATURE_SERVICE" : "ITEM_NATURE_GOODS");
            detail.put("unit_price", line.unitPrice());
            detail.put("quantity", line.quantity());
            detail.put("tax_amount", ZERO_AMOUNT);
            detail.put("total_amount", ZERO_AMOUNT);
            detail.put("classification_code", line.classificationCode());
            detail.put("quantity_code", QUANTITY_CODE);
            detail.put("country_code", COUNTRY_CODE);
            detail.put("tax_rate", line.taxRatePercent());
            detail.put("tax_code", line.taxCode() != null && !line.taxCode().isBlank() ? line.taxCode() : DEFAULT_TAX_CODE);
            detail.put("gross_amount", ZERO_AMOUNT);
            detail.put("item_sequence", line.sequence());
            detail.put("discount_amount", ZERO_AMOUNT);
            detail.put("discount_rate", line.discountPercent());
        }

        request.putNull("refund_reference");
        return request;
    }

    /**
     * Task: cis_date maps to "Order date in this format '2026-01-04 10:03:41'" — Fourier's own
     * correction: this is {@link EfrisChadInvoicePayload#orderDate()} ({@code
     * SaleOrder.completedAt}, when the sale itself completed), not wall-clock time at submission,
     * so a retried submission still reports the original sale's own date/time. Falls back to
     * "now" only if a payload somehow arrives with no order date at all (shouldn't happen for a
     * completed sale, but this never throws rather than fail the whole submission over a
     * formatting edge case).
     */
    private String formatOrderDate(Instant orderDate) {
        Instant at = orderDate != null ? orderDate : Instant.now();
        return LocalDateTime.ofInstant(at, ZoneId.systemDefault()).format(CIS_DATE_FORMAT);
    }

    /**
     * Fourier's own rule: "When response_code is 'P' means SUCCESS." On success: "Second value is
     * sdc_id / Third value is MRC / Fourth is sdc_receipt_number / Next is sdc_date / Next is
     * signature_data / Next is internal_data" splits {@code response_data.sdc_data} (Task, see
     * {@link EfrisChadSubmissionResult}'s javadoc for the full positional mapping), Task 2: "The
     * cis_number is picked from tag invoice_number of json response" reads {@code
     * response_data.invoice_number} directly (works whether EFRIS Chad sends it as a JSON number,
     * as the sample shows, or a string), and Task 1: "The cis_data for sale_order should be the
     * one send to json cis_date" passes through the exact {@code cisDate} string this same
     * request already sent, computed once by {@link #submit} rather than reformatted here.
     */
    private EfrisChadSubmissionResult parseResponse(String body, String cisDate) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        String responseCode = root.path("response_code").asText(null);
        if (!"P".equals(responseCode)) {
            String message = root.has("response_message") ? root.path("response_message").asText() : body;
            return EfrisChadSubmissionResult.failure("EFRIS Chad rejected the invoice: " + message, body);
        }
        JsonNode responseData = root.path("response_data");
        String sdcData = responseData.path("sdc_data").asText(null);
        String cisNumber = responseData.path("invoice_number").asText(null);
        return EfrisChadSubmissionResult.success(sdcData, cisNumber, cisDate, body);
    }
}
