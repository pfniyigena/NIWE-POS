package com.posapp.onlinestore.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posapp.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A pretend provider for development, demos and tests: the customer lands on a test page and presses Pay or Decline. It speaks the same signed
 * webhook as a real provider would, so the whole payment path (signature check, duplicates, late payments, refunds) is exercised for real.
 * Its state is kept in memory only. Never enable it on a live shop: no money moves.
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    public static final String CODE = "SIMULATED";
    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProvider.class);

    private final boolean enabled;
    private final String secret;
    private final String frontendUrl;
    private final Environment environment;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Sim> payments = new ConcurrentHashMap<>();

    public SimulatedPaymentProvider(@Value("${app.online-store.payments.simulated.enabled:false}") boolean enabled,
                                    @Value("${app.online-store.payments.simulated.secret:}") String secret,
                                    @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl, Environment environment) {
        this.enabled = enabled;
        this.secret = secret == null ? "" : secret;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.environment = environment;
    }

    @PostConstruct
    void warn() {
        if (!enabled) return;
        boolean dev = List.of(environment.getActiveProfiles()).contains("dev");
        if (!dev) log.warn("The SIMULATED payment provider is enabled outside the dev profile: no real money moves. Switch it off on a live shop.");
        if (secret.length() < 8) log.warn("app.online-store.payments.simulated.secret is empty or too short: the simulated webhook cannot be signed, so no simulated payment will be confirmed.");
    }

    /** A test payment as the pretend provider sees it. */
    private static final class Sim {
        final BigDecimal amount;
        final String currency;
        final String method;
        volatile State state = State.PENDING;
        volatile String reason;

        Sim(BigDecimal amount, String currency, String method) {
            this.amount = amount;
            this.currency = currency;
            this.method = method;
        }
    }

    /** What the test page shows. */
    public record TestPayment(String reference, BigDecimal amount, String currency, String method, String state) {
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Simulated (test only, no real money)";
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public List<String> methods() {
        return List.of("MOBILE_MONEY", "CARD");
    }

    @Override
    public Initiated initiate(PaymentRequest r) {
        payments.put(r.reference(), new Sim(r.amount(), r.currency(), r.method()));
        return new Initiated("SIM-" + r.reference(), frontendUrl + "/shop/pay-test/" + r.reference(), null);
    }

    @Override
    public StatusResult status(String reference) {
        Sim s = payments.get(reference);
        if (s == null) return new StatusResult(State.PENDING, null, null, null, null);
        return new StatusResult(s.state, s.amount, s.currency, "SIM-" + reference, s.reason);
    }

    @Override
    public RefundResult refund(String reference, BigDecimal amount, String reason) {
        return new RefundResult(true, "SIMREF-" + reference, null);
    }

    @Override
    public boolean verifySignature(String rawBody, Map<String, String> headers) {
        if (!enabled || secret.length() < 8 || rawBody == null) return false;
        String given = headers == null ? null : headers.get("x-signature");
        if (given == null) return false;
        return MessageDigest.isEqual(sign(rawBody).getBytes(StandardCharsets.UTF_8), given.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        try {
            JsonNode n = json.readTree(rawBody);
            String st = n.path("status").asText("").toUpperCase();
            State state = st.equals("PAID") ? State.PAID : st.equals("FAILED") ? State.FAILED : State.PENDING;
            BigDecimal amount = n.hasNonNull("amount") ? new BigDecimal(n.get("amount").asText()) : null;
            return new WebhookEvent(n.path("reference").asText(null), state, amount, n.hasNonNull("currency") ? n.get("currency").asText() : null,
                    n.hasNonNull("providerReference") ? n.get("providerReference").asText() : null, n.hasNonNull("reason") ? n.get("reason").asText() : null);
        } catch (Exception e) {
            throw ApiException.badRequest("The webhook body is not valid");
        }
    }

    // ------------------------------------------------------------------ the test page

    public TestPayment find(String reference) {
        Sim s = require(reference);
        return new TestPayment(reference, s.amount, s.currency, s.method, s.state.name());
    }

    /** The customer pressed Pay (approve) or Decline on the test page: the signed webhook body the pretend provider would post. */
    public Signed decide(String reference, boolean approve) {
        Sim s = require(reference);
        if (s.state == State.PENDING) {
            s.state = approve ? State.PAID : State.FAILED;
            if (!approve) s.reason = "Declined on the test page";
        }
        return signedEvent(reference, s.state, s.amount, s.currency, s.reason);
    }

    /** A signed webhook body for any state or amount, for tests and the smoke script. */
    public Signed signedEvent(String reference, State state, BigDecimal amount, String currency, String reason) {
        StringBuilder b = new StringBuilder("{\"reference\":\"").append(reference).append("\",\"status\":\"").append(state).append("\"");
        if (amount != null) b.append(",\"amount\":\"").append(amount.toPlainString()).append("\"");
        if (currency != null) b.append(",\"currency\":\"").append(currency).append("\"");
        b.append(",\"providerReference\":\"SIM-").append(reference).append("\"");
        if (reason != null) b.append(",\"reason\":\"").append(reason.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        b.append("}");
        String body = b.toString();
        return new Signed(body, sign(body));
    }

    public record Signed(String body, String signature) {
    }

    private Sim require(String reference) {
        if (!enabled) throw ApiException.notFound("Not found.");
        Sim s = payments.get(reference);
        if (s == null) throw ApiException.notFound("Not found.");
        return s;
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
