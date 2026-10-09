package com.posapp.apps.efris;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.posapp.apps.efris.dto.EfrisConnection;
import com.posapp.apps.efris.dto.EfrisInvoiceLine;
import com.posapp.apps.efris.dto.EfrisInvoicePayload;
import com.posapp.apps.efris.dto.EfrisSubmissionResult;
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
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Submits one completed sale to EFRIS over the JSON-RPC protocol Fourier's own sample used —
 * Odoo's generic {@code /jsonrpc} "object.execute" calling convention, invoking model {@code
 * api.invoice}, method {@code create_api}. Deliberately has zero reference to {@code SaleOrder}/
 * {@code Register}/{@code Customer}: everything it needs arrives as {@link EfrisConnection}/
 * {@link EfrisInvoicePayload}, plain records {@code OrderService} builds from its own entities,
 * keeping {@code apps.efris} free of any dependency back on {@code order} (see the
 * one-directional cross-module dependency convention documented on {@link EfrisConnection}).
 * <p>
 * Uses the JDK's built-in {@link HttpClient} rather than adding an HTTP client dependency this
 * codebase hasn't already verified. Never throws outward: any failure — network, non-200, a
 * JSON-RPC-level {@code error}, or a non-blank {@code result.error} — comes back as an {@link
 * EfrisSubmissionResult#failure} so a down or misbehaving EFRIS endpoint never blocks a
 * checkout (the "sale still completes, flagged for review" policy lives in {@code OrderService},
 * which is the only caller of this method).
 * <p>
 * Every submission logs its outbound request and inbound response at {@code INFO}, tagged with
 * the invoice number ({@code erp_code}) so a real submission can be traced end to end in the
 * application log — there's no admin-UI log viewer for this, just the server's own log output.
 * The logged request is never the literal one sent over the wire: {@link #redactedRequestJson}
 * replaces {@code args[2]} (the EFRIS connection password) with a fixed placeholder before
 * serializing it for logging, so a terminal's EFRIS password is never written to disk in plain
 * text just because a sale happened to be rung — the real, unredacted request is still what
 * actually goes out over HTTP.
 */
@Service
public class EfrisSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(EfrisSubmissionService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Fourier's own request: "the amount value in json should be type of double with 2 decimals"
     * — {@code tax_amount}/{@code discount_amount}/{@code gross_amount} were being sent as the
     * plain integer {@code 0} (see the class javadoc for why they're zero at all: the sample lets
     * EFRIS compute these server-side from {@code unitPrice}/{@code quantity}/
     * {@code discountPercent} rather than this app reproducing EFRIS's own tax math). A
     * {@link BigDecimal} scaled to exactly 2 decimal places is what actually produces a fixed
     * two-decimal JSON number (Jackson serializes a {@code BigDecimal} using its own scale, e.g.
     * {@code "0.00"}) — a Java {@code double} literal can't guarantee that (Jackson would print a
     * bare {@code 0.0} for {@code 0.0d}), so this reuses the same money-shaped representation
     * {@code unit_price}/{@code tax_rate}/{@code discount_rate} already send.
     */
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EfrisSubmissionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public EfrisSubmissionResult submit(EfrisConnection connection, EfrisInvoicePayload payload) {
        String invoiceRef = payload != null ? payload.erpCode() : null;
        if (connection == null || connection.url() == null || connection.url().isBlank()) {
            return EfrisSubmissionResult.failure("No EFRIS URL configured for this terminal");
        }
        ObjectNode requestTree;
        String requestBody;
        try {
            requestTree = buildRequestTree(connection, payload);
            requestBody = objectMapper.writeValueAsString(requestTree);
        } catch (Exception e) {
            log.warn("Failed to build EFRIS request body for invoice {}", invoiceRef, e);
            return EfrisSubmissionResult.failure("Failed to build EFRIS request: " + e.getMessage());
        }

        log.info("EFRIS request for invoice {} -> {}: {}", invoiceRef, connection.url(), redactedRequestJson(requestTree));

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(connection.url()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
        } catch (IllegalArgumentException e) {
            return EfrisSubmissionResult.failure("Invalid EFRIS URL: " + connection.url());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            log.warn("EFRIS submission failed for invoice {} at {}", invoiceRef, connection.url(), e);
            return EfrisSubmissionResult.failure("Could not reach EFRIS: " + e.getMessage());
        }

        log.info("EFRIS response for invoice {} <- {} (HTTP {}): {}",
                invoiceRef, connection.url(), response.statusCode(), response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return EfrisSubmissionResult.failure("EFRIS returned HTTP " + response.statusCode(), response.body());
        }

        try {
            return parseResponse(response.body());
        } catch (Exception e) {
            log.warn("Failed to parse EFRIS response for invoice {}: {}", invoiceRef, response.body(), e);
            return EfrisSubmissionResult.failure("Could not parse EFRIS response: " + e.getMessage(), response.body());
        }
    }

    /**
     * Builds a copy of the outbound request tree with the connection password ({@code
     * args[2]}) replaced by a fixed placeholder, safe to write to the application log — see the
     * class javadoc. Falls back to a placeholder string (never the real tree, redacted or not)
     * if the copy/redaction itself fails for any reason, so a bug in this method can never result
     * in the unredacted password being logged instead.
     */
    private String redactedRequestJson(ObjectNode requestTree) {
        try {
            ObjectNode redacted = requestTree.deepCopy();
            JsonNode argsNode = redacted.path("params").path("args");
            if (argsNode instanceof ArrayNode args && args.size() > 2) {
                args.set(2, TextNode.valueOf("***REDACTED***"));
            }
            return objectMapper.writeValueAsString(redacted);
        } catch (Exception e) {
            return "<EFRIS request body omitted from log: could not redact password: " + e.getMessage() + ">";
        }
    }

    private ObjectNode buildRequestTree(EfrisConnection connection, EfrisInvoicePayload payload) {
        ObjectNode invoice = objectMapper.createObjectNode();
        invoice.putNull("currency");
        invoice.put("currencyRate", 0);
        invoice.put("user_id", connection.uid());
        invoice.put("company_tin", payload.companyTin());
        invoice.put("client_tin", payload.clientTin());
        invoice.put("client_name", payload.clientName());
        invoice.put("client_phone", payload.clientPhone());
        invoice.put("client_address", payload.clientAddress());
        invoice.put("erp_code", payload.erpCode());
        invoice.put("erp_code_ref", payload.erpCodeRef());
        invoice.put("transaction_type", payload.transactionType());
        invoice.put("invoice_type", "N");

        ArrayNode sales = invoice.putArray("sales");
        for (EfrisInvoiceLine line : payload.lines()) {
            ObjectNode saleLine = sales.addObject();
            saleLine.put("product_name", line.productName());
            saleLine.put("product_code", line.productCode());
            saleLine.put("quantity", line.quantity());
            saleLine.put("unit_price", line.unitPrice());
            saleLine.put("tax_rate", line.taxRatePercent());
            saleLine.put("tax_label", line.taxLabel());
            saleLine.put("tax_amount", ZERO_AMOUNT);
            saleLine.put("unsp_code", line.unspCode());
            saleLine.put("discount_rate", line.discountPercent());
            saleLine.put("discount_amount", ZERO_AMOUNT);
            saleLine.put("gross_amount", ZERO_AMOUNT);
        }
        if (payload.paymentMode() != null && !payload.paymentMode().isBlank()) {
            invoice.put("payment_mode", payload.paymentMode());
        } else {
            invoice.putNull("payment_mode");
        }

        ArrayNode args = objectMapper.createArrayNode();
        args.add(connection.db());
        args.add(connection.uid());
        args.add(connection.password());
        args.add("api.invoice");
        args.add("create_api");
        args.add(invoice);

        ObjectNode params = objectMapper.createObjectNode();
        params.set("args", args);
        params.put("method", "execute");
        params.put("service", "object");

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
        request.put("jsonrpc", "2.0");
        request.put("method", "call");
        request.set("params", params);
        return request;
    }

    private EfrisSubmissionResult parseResponse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode rpcError = root.get("error");
        if (rpcError != null && !rpcError.isNull()) {
            return EfrisSubmissionResult.failure("EFRIS error: " + rpcError, body);
        }
        JsonNode result = root.get("result");
        if (result == null || result.isNull()) {
            return EfrisSubmissionResult.failure("EFRIS response had no result", body);
        }
        // Fourier's own rule: "if error is not null and not empty that is when we record error" —
        // failure is decided by result.error being present and non-blank, not by requiring it to
        // equal the literal "SUCCESS" (EFRIS's success responses don't always send that literal).
        String status = result.path("error").asText(null);
        if (status != null && !status.isBlank()) {
            String detail = result.has("message") ? result.path("message").asText() : result.toString();
            return EfrisSubmissionResult.failure("EFRIS rejected the invoice: " + detail, body);
        }
        String sdcData = result.path("sdc_data").asText(null);
        String cisData = result.path("cis_data").asText(null);
        return EfrisSubmissionResult.success(sdcData, cisData, body);
    }
}
