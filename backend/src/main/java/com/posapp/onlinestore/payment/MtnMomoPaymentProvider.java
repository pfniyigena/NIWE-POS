package com.posapp.onlinestore.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * MTN Mobile Money (MoMo) Open API, Collection product: the customer is asked on their phone to approve a payment ("request to pay").
 *
 * <p>How it works: {@link #initiate} sends the request and returns at once; the customer approves on the phone. MTN then tells us by a callback
 * (sent once, never retried) and the shop's status job asks MTN about every minute as well. <b>MTN callbacks are not signed.</b> So nothing in a
 * callback is trusted: {@link #verifySignature} asks MTN itself for the payment's status and only accepts the callback when MTN's own answer says
 * the same (state, amount, currency, our reference). A forged callback therefore changes nothing.
 *
 * <p>Our payment reference travels as MTN's {@code externalId}. MTN wants its own UUID (v4) per request in {@code X-Reference-Id}; it is derived
 * from our reference with an HMAC keyed by the API key, so it is repeatable (status checks and refunds find the payment with no stored mapping),
 * valid as a v4 UUID and impossible to guess.
 *
 * <p>All settings come from properties or the environment (never the database), see docs/online-store-mtn-momo.md:
 * {@code app.online-store.payments.mtn.*}. Not available until the subscription key, API user and API key are set.
 */
@Component
public class MtnMomoPaymentProvider implements PaymentProvider {

    public static final String CODE = "MTN_MOMO";
    private static final Logger log = LoggerFactory.getLogger(MtnMomoPaymentProvider.class);
    private static final Pattern REFERENCE = Pattern.compile("^OS[A-Z0-9]{3,20}-[0-9]{1,3}$");

    private final boolean enabled;
    private final String baseUrl;
    private final String targetEnvironment;
    /** In the sandbox MTN only knows EUR, so requests go out in EUR whatever the shop's currency is and the currency MTN reports back is not compared (the amount still is). */
    private final boolean sandbox;
    private static final String SANDBOX_CURRENCY = "EUR";
    private final String subscriptionKey;
    private final String apiUser;
    private final String apiKey;
    private final String callbackUrl;
    private final String countryCode;
    private final String refundPath;
    private final int refundWaitSeconds;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    private String token;
    private Instant tokenUntil = Instant.EPOCH;
    /** Callbacks checked in the last minute: anyone can send a callback, and each one costs a call to MTN, so only so many are checked. */
    private final java.util.ArrayDeque<Long> checks = new java.util.ArrayDeque<>();
    static final int CHECKS_PER_MINUTE = 60;

    public MtnMomoPaymentProvider(@Value("${app.online-store.payments.mtn.enabled:false}") boolean enabled,
                                  @Value("${app.online-store.payments.mtn.base-url:https://sandbox.momodeveloper.mtn.com}") String baseUrl,
                                  @Value("${app.online-store.payments.mtn.target-environment:sandbox}") String targetEnvironment,
                                  @Value("${app.online-store.payments.mtn.subscription-key:}") String subscriptionKey,
                                  @Value("${app.online-store.payments.mtn.api-user:}") String apiUser,
                                  @Value("${app.online-store.payments.mtn.api-key:}") String apiKey,
                                  @Value("${app.online-store.payments.mtn.callback-url:}") String callbackUrl,
                                  @Value("${app.online-store.payments.mtn.country-code:250}") String countryCode,
                                  @Value("${app.online-store.payments.mtn.refund-path:/collection/v2_0/refund}") String refundPath,
                                  @Value("${app.online-store.payments.mtn.refund-wait-seconds:6}") int refundWaitSeconds,
                                  @Value("${app.online-store.payments.mtn.timeout-seconds:15}") int timeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = trim(baseUrl).replaceAll("/+$", "");
        this.targetEnvironment = trim(targetEnvironment);
        this.sandbox = this.targetEnvironment.equalsIgnoreCase("sandbox");
        this.subscriptionKey = trim(subscriptionKey);
        this.apiUser = trim(apiUser);
        this.apiKey = trim(apiKey);
        this.callbackUrl = trim(callbackUrl);
        this.countryCode = trim(countryCode).isEmpty() ? "250" : trim(countryCode);
        this.refundPath = trim(refundPath).isEmpty() ? "/collection/v2_0/refund" : "/" + trim(refundPath).replaceAll("^/+", "").replaceAll("/+$", "");
        this.refundWaitSeconds = Math.max(0, Math.min(20, refundWaitSeconds));
        this.timeout = Duration.ofSeconds(Math.max(3, Math.min(60, timeoutSeconds)));
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    @PostConstruct
    void warn() {
        if (!enabled) return;
        if (!available()) log.warn("The MTN_MOMO payment provider is switched on but not usable: it needs the subscription key, API user, API key and an https base URL.");
        else if (callbackUrl.isEmpty()) log.info("MTN_MOMO: no callback URL set, payments are confirmed by the status check only (about once a minute).");
        if (available() && targetEnvironment.equalsIgnoreCase("sandbox")) log.warn("MTN_MOMO uses the SANDBOX: no real money moves and payments are requested in EUR whatever the shop currency is. Set the live target environment and base URL for a live shop.");
    }

    // ------------------------------------------------------------------ the interface

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "MTN Mobile Money (MoMo)";
    }

    @Override
    public boolean available() {
        if (!enabled || subscriptionKey.isEmpty() || apiUser.isEmpty() || apiKey.isEmpty() || targetEnvironment.isEmpty()) return false;
        // Keys travel in the headers: only https, except a server on this machine (tests).
        return baseUrl.startsWith("https://") || baseUrl.startsWith("http://127.0.0.1") || baseUrl.startsWith("http://localhost");
    }

    @Override
    public List<String> methods() {
        return List.of("MOBILE_MONEY");
    }

    @Override
    public Initiated initiate(PaymentRequest r) {
        String uuid = uuid(r.reference());
        String msisdn = msisdn(r.phone(), countryCode);
        if (msisdn.length() < 9) throw ApiException.badRequest(I18n.t("Enter the mobile money number to pay from"));
        ObjectNode body = json.createObjectNode();
        body.put("amount", amount(r.amount()));
        body.put("currency", sandbox ? SANDBOX_CURRENCY : r.currency());
        body.put("externalId", r.reference());
        ObjectNode payer = body.putObject("payer");
        payer.put("partyIdType", "MSISDN");
        payer.put("partyId", msisdn);
        String note = r.description() == null ? "" : r.description().length() > 100 ? r.description().substring(0, 100) : r.description();
        body.put("payerMessage", note);
        body.put("payeeNote", note);
        Resp resp = call("POST", "/collection/v1_0/requesttopay", uuid, callbackUrl.isEmpty() ? null : callbackUrl, body.toString());
        if (resp.status == 202 || resp.status == 200) return new Initiated(uuid, null, null);
        // The same reference asked twice: MTN already has it, so the customer was already asked.
        if (resp.status == 409) return new Initiated(uuid, null, null);
        String code = errorCode(resp.body);
        log.warn("MTN MoMo refused the request to pay {} ({}): HTTP {} {}", r.reference(), mask(msisdn), resp.status, code);
        if ("PAYER_NOT_FOUND".equals(code)) throw ApiException.badRequest(I18n.t("That number has no MTN mobile money account. Check it or choose another way to pay."));
        throw new IllegalStateException("MTN MoMo answered HTTP " + resp.status + (code.isEmpty() ? "" : " " + code));
    }

    @Override
    public StatusResult status(String reference) {
        Remote r = fetch(uuid(reference));
        if (r == null) return new StatusResult(State.PENDING, null, null, null, null);
        return new StatusResult(r.state, r.amount, sandbox ? null : r.currency, r.transactionId, r.reason);
    }

    /**
     * Gives money back through MTN's refund call. The refund is asynchronous at MTN, so after asking we wait a few seconds for its answer: only a
     * refund MTN reports as successful is reported as done. If MTN has not answered yet, or says no, staff are told and refund by hand with the reference.
     *
     * <p>The refund's MTN id is derived from the payment, the amount and the note, so asking again for the same refund (a second click, a retry after
     * a timeout) makes MTN answer "already exists" and we simply look at that refund again: money is never given back twice by a repeated click.
     */
    @Override
    public RefundResult refund(String reference, BigDecimal amount, String reason) {
        String payment = uuid(reference);
        Remote paid;
        try {
            paid = fetch(payment);
        } catch (RuntimeException e) {
            return new RefundResult(false, null, I18n.t("MTN could not be reached to check the payment. Refund it by hand and enter the reference."));
        }
        if (paid == null || paid.state != State.PAID || paid.currency == null) {
            return new RefundResult(false, null, I18n.t("MTN does not show this payment as successful. Refund it by hand and enter the reference."));
        }
        String note = reason == null ? "" : reason.trim();
        if (note.length() > 100) note = note.substring(0, 100);
        String refundId = derive("momo-refund:" + reference + ":" + amount(amount) + ":" + note);
        ObjectNode body = json.createObjectNode();
        body.put("amount", amount(amount));
        body.put("currency", paid.currency);
        body.put("externalId", reference);
        body.put("payerMessage", note);
        body.put("payeeNote", note);
        body.put("referenceIdToRefund", payment);
        Resp resp;
        try {
            resp = call("POST", refundPath, refundId, null, body.toString());
        } catch (RuntimeException e) {
            return new RefundResult(false, null, I18n.t("MTN could not be reached for the refund. Refund it by hand and enter the reference."));
        }
        // 409: this very refund was already asked for; look at it instead of asking again.
        if (resp.status != 202 && resp.status != 200 && resp.status != 409) {
            String code = errorCode(resp.body);
            log.warn("MTN MoMo refused the refund of {}: HTTP {} {}", reference, resp.status, code);
            return new RefundResult(false, null, code.isEmpty() ? I18n.t("MTN did not accept the refund. Refund it by hand and enter the reference.")
                    : I18n.t("MTN did not accept the refund ({0}). Refund it by hand and enter the reference.", code));
        }
        for (int i = 0; i <= refundWaitSeconds; i++) {
            Remote r;
            try {
                r = fetchAt(refundPath + "/" + refundId);
            } catch (RuntimeException e) {
                r = null;
            }
            if (r != null && r.state == State.PAID) return new RefundResult(true, refundId, null);
            if (r != null && r.state == State.FAILED) {
                return new RefundResult(false, null, r.reason == null || r.reason.isBlank() ? I18n.t("MTN refused the refund. Refund it by hand and enter the reference.")
                        : I18n.t("MTN refused the refund ({0}). Refund it by hand and enter the reference.", r.reason));
            }
            if (i < refundWaitSeconds) sleep(1000);
        }
        return new RefundResult(false, null, I18n.t("MTN has not confirmed the refund yet (MTN reference {0}). Look at it in your MTN portal. If it went through, "
                + "record it with \"I refunded it myself\" and this reference. Do not refund it twice.", refundId));
    }

    /**
     * MTN does not sign callbacks, so this asks MTN. The callback is accepted only when MTN's own record of the payment (found by our reference)
     * is no longer pending and matches the callback in state, amount, currency and reference. Anything else is refused and changes nothing.
     */
    @Override
    public boolean verifySignature(String rawBody, Map<String, String> headers) {
        if (!available() || rawBody == null || rawBody.length() > 8000) return false;
        try {
            JsonNode n = json.readTree(rawBody);
            String reference = n.path("externalId").asText("");
            if (!REFERENCE.matcher(reference).matches()) return false;
            // Over the limit the callback is refused; nothing is lost, the status check (about once a minute) finds the payment anyway.
            if (!allowCheck()) {
                warnFlood();
                return false;
            }
            Remote real = fetch(uuid(reference));
            if (real == null || real.state == State.PENDING || !reference.equals(real.externalId)) return false;
            Event said = parse(n);
            if (said.state != real.state) return false;
            if (said.amount != null && real.amount != null && said.amount.compareTo(real.amount) != 0) return false;
            if (said.currency != null && real.currency != null && !said.currency.equalsIgnoreCase(real.currency)) return false;
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not verify an MTN MoMo callback: {}", e.getMessage());
            return false;
        }
    }

    private long floodWarnedAt;

    private synchronized void warnFlood() {
        long now = System.currentTimeMillis();
        if (now - floodWarnedAt < 60_000) return;
        floodWarnedAt = now;
        log.warn("MTN_MOMO: more than {} callbacks in a minute, the extra ones are not checked (the status check still finds real payments)", CHECKS_PER_MINUTE);
    }

    private synchronized boolean allowCheck() {
        long now = System.currentTimeMillis();
        while (!checks.isEmpty() && now - checks.peekFirst() > 60_000) checks.pollFirst();
        if (checks.size() >= CHECKS_PER_MINUTE) return false;
        checks.addLast(now);
        return true;
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        try {
            Event e = parse(json.readTree(rawBody));
            return new WebhookEvent(e.reference, e.state, e.amount, sandbox ? null : e.currency, e.transactionId, e.reason);
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("The webhook body is not valid");
        }
    }

    // ------------------------------------------------------------------ what MTN says

    private record Event(String reference, State state, BigDecimal amount, String currency, String transactionId, String reason) {
    }

    private record Remote(State state, BigDecimal amount, String currency, String transactionId, String reason, String externalId) {
    }

    private static State state(String s) {
        String v = s == null ? "" : s.trim().toUpperCase();
        return switch (v) {
            case "SUCCESSFUL" -> State.PAID;
            case "FAILED", "REJECTED", "TIMEOUT" -> State.FAILED;
            default -> State.PENDING;
        };
    }

    private static Event parse(JsonNode n) {
        BigDecimal amount = null;
        if (n.hasNonNull("amount")) {
            try {
                amount = new BigDecimal(n.get("amount").asText().trim());
            } catch (NumberFormatException e) {
                amount = null;
            }
        }
        return new Event(n.path("externalId").asText(null), state(n.path("status").asText("")), amount, n.hasNonNull("currency") ? n.get("currency").asText() : null,
                n.hasNonNull("financialTransactionId") ? n.get("financialTransactionId").asText() : null, reason(n.path("reason")));
    }

    /** MTN gives the reason as a code, or as an object with a code and a message. */
    private static String reason(JsonNode r) {
        if (r == null || r.isMissingNode() || r.isNull()) return null;
        if (r.isTextual()) return r.asText();
        String code = r.path("code").asText("");
        String msg = r.path("message").asText("");
        String out = (code + (msg.isEmpty() ? "" : ": " + msg)).trim();
        return out.isEmpty() ? null : out;
    }

    private Remote fetch(String uuid) {
        return fetchAt("/collection/v1_0/requesttopay/" + uuid);
    }

    /** Null when MTN does not know the id (404). Throws when MTN cannot be reached or answers something unexpected. */
    private Remote fetchAt(String path) {
        Resp resp = call("GET", path, null, null, null);
        if (resp.status == 404) return null;
        if (resp.status != 200) throw new IllegalStateException("MTN MoMo answered HTTP " + resp.status + " for a status check");
        try {
            JsonNode n = json.readTree(resp.body);
            Event e = parse(n);
            return new Remote(e.state, e.amount, e.currency, e.transactionId, e.reason, n.path("externalId").asText(null));
        } catch (IOException e) {
            throw new IllegalStateException("MTN MoMo answered something that is not JSON");
        }
    }

    // ------------------------------------------------------------------ the HTTP calls

    private record Resp(int status, String body) {
    }

    /** One call to MTN with the token; a 401 gets one fresh token and one retry. referenceId (a UUID) and callback are sent when given. */
    private Resp call(String method, String path, String referenceId, String callback, String body) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String bearer = token(attempt > 0);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
                    .header("Authorization", "Bearer " + bearer).header("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .header("X-Target-Environment", targetEnvironment).header("Accept", "application/json");
            if (referenceId != null) b.header("X-Reference-Id", referenceId);
            if (callback != null) b.header("X-Callback-Url", callback);
            if (body != null) b.header("Content-Type", "application/json");
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            Resp r = send(b.build());
            if (r.status != 401 || attempt > 0) return r;
        }
        throw new IllegalStateException("unreachable");
    }

    private synchronized String token(boolean force) {
        if (!force && token != null && Instant.now().isBefore(tokenUntil)) return token;
        String basic = Base64.getEncoder().encodeToString((apiUser + ":" + apiKey).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/collection/token/")).timeout(timeout)
                .header("Authorization", "Basic " + basic).header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .header("X-Target-Environment", targetEnvironment).header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        Resp r = send(req);
        if (r.status != 200) throw new IllegalStateException("MTN MoMo refused to give an access token: HTTP " + r.status);
        try {
            JsonNode n = json.readTree(r.body);
            String t = n.path("access_token").asText("");
            if (t.isEmpty()) throw new IllegalStateException("MTN MoMo gave no access token");
            long seconds = n.path("expires_in").asLong(3600);
            token = t;
            tokenUntil = Instant.now().plusSeconds(Math.max(30, seconds - 60));
            return t;
        } catch (IOException e) {
            throw new IllegalStateException("MTN MoMo gave an access token answer that is not JSON");
        }
    }

    private Resp send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resp(res.statusCode(), res.body() == null ? "" : res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The call to MTN MoMo was interrupted");
        } catch (IOException e) {
            throw new IllegalStateException("MTN MoMo could not be reached: " + e.getClass().getSimpleName());
        }
    }

    private String errorCode(String body) {
        try {
            return json.readTree(body).path("code").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ small helpers

    /** The UUID v4 that stands for one of our references at MTN: repeatable, valid, and not guessable without the API key. */
    String uuid(String reference) {
        return derive("momo:" + reference);
    }

    private String derive(String seed) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(seed.getBytes(StandardCharsets.UTF_8));
            h[6] = (byte) ((h[6] & 0x0f) | 0x40);
            h[8] = (byte) ((h[8] & 0x3f) | 0x80);
            long hi = 0, lo = 0;
            for (int i = 0; i < 8; i++) hi = (hi << 8) | (h[i] & 0xff);
            for (int i = 8; i < 16; i++) lo = (lo << 8) | (h[i] & 0xff);
            return new UUID(hi, lo).toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive the MTN reference");
        }
    }

    /** Digits only, in international form without the plus: 0788 123 456 and +250 788 123 456 become 250788123456. */
    static String msisdn(String phone, String countryCode) {
        String d = phone == null ? "" : phone.replaceAll("\\D", "");
        if (d.startsWith("00")) d = d.substring(2);
        if (d.startsWith("0")) return countryCode + d.substring(1);
        if (!d.startsWith(countryCode) && d.length() <= 9) return countryCode + d;
        return d;
    }

    /** MTN takes the amount as text. No trailing zeros: 1000, not 1.0E+3 or 1000.00 (RWF has no decimals). */
    static String amount(BigDecimal a) {
        BigDecimal v = a.stripTrailingZeros();
        return (v.scale() < 0 ? v.setScale(0) : v).toPlainString();
    }

    private static String mask(String number) {
        return number == null || number.length() < 6 ? "***" : number.substring(0, 5) + "***" + number.substring(number.length() - 2);
    }
}
