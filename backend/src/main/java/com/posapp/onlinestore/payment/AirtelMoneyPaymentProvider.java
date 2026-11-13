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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Airtel Money Open API, Collection product ("USSD push"): the customer is asked on their phone to approve a payment.
 *
 * <p>How it works: {@link #initiate} sends the request and returns at once; the customer approves with their PIN. Airtel then posts a callback to
 * the address registered on the app in the Airtel developer portal, and the shop's status job asks Airtel about every minute as well.
 * <b>Nothing in a callback is trusted</b> (how Airtel signs callbacks is not something this shop relies on): {@link #verifySignature} asks Airtel
 * itself for the payment's status by our reference and only accepts the callback when Airtel's own answer is final and says the same. A forged
 * callback therefore changes nothing.
 *
 * <p>Our payment reference travels as Airtel's {@code transaction.id}; status checks use the same id, so nothing has to be stored. Airtel's
 * enquiry does not return the amount, so the amount is not compared (the shop asked for it and Airtel took exactly that); the status and
 * Airtel's own reference ({@code airtel_money_id}) are.
 *
 * <p>Refunds are <b>not</b> sent to Airtel: Airtel's refund call gives back a whole payment, and the shop cannot tell it that only part of the
 * order is returned. Staff refund in the Airtel portal or from the Airtel wallet and record it with "I refunded it myself".
 *
 * <p>All settings come from properties or the environment (never the database), see docs/online-store-airtel-money.md:
 * {@code app.online-store.payments.airtel.*}. Not available until the client id and client secret are set.
 */
@Component
public class AirtelMoneyPaymentProvider implements PaymentProvider {

    public static final String CODE = "AIRTEL_MONEY";
    private static final Logger log = LoggerFactory.getLogger(AirtelMoneyPaymentProvider.class);
    private static final Pattern REFERENCE = Pattern.compile("^OS[A-Z0-9]{3,20}-[0-9]{1,3}$");
    static final int CHECKS_PER_MINUTE = 60;

    private final boolean enabled;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String country;
    private final String currencyOverride;
    private final String countryCode;
    private final String pushPath;
    private final String statusPath;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    private String token;
    private Instant tokenUntil = Instant.EPOCH;
    private final Deque<Long> checks = new ArrayDeque<>();

    public AirtelMoneyPaymentProvider(@Value("${app.online-store.payments.airtel.enabled:false}") boolean enabled,
                                      @Value("${app.online-store.payments.airtel.base-url:https://openapiuat.airtel.africa}") String baseUrl,
                                      @Value("${app.online-store.payments.airtel.client-id:}") String clientId,
                                      @Value("${app.online-store.payments.airtel.client-secret:}") String clientSecret,
                                      @Value("${app.online-store.payments.airtel.country:RW}") String country,
                                      @Value("${app.online-store.payments.airtel.currency:}") String currencyOverride,
                                      @Value("${app.online-store.payments.airtel.country-code:250}") String countryCode,
                                      @Value("${app.online-store.payments.airtel.push-path:/merchant/v1/payments/}") String pushPath,
                                      @Value("${app.online-store.payments.airtel.status-path:/standard/v1/payments/}") String statusPath,
                                      @Value("${app.online-store.payments.airtel.timeout-seconds:15}") int timeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = trim(baseUrl).replaceAll("/+$", "");
        this.clientId = trim(clientId);
        this.clientSecret = trim(clientSecret);
        this.country = trim(country).isEmpty() ? "RW" : trim(country).toUpperCase();
        this.currencyOverride = trim(currencyOverride).toUpperCase();
        this.countryCode = trim(countryCode).isEmpty() ? "250" : trim(countryCode);
        this.pushPath = path(pushPath, true);
        this.statusPath = path(statusPath, true);
        this.timeout = Duration.ofSeconds(Math.max(3, Math.min(60, timeoutSeconds)));
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** A path that starts with one slash; with the trailing slash when asked (Airtel's paths end with one). */
    private static String path(String p, boolean trailing) {
        String v = "/" + trim(p).replaceAll("^/+", "").replaceAll("/+$", "");
        return trailing ? v + "/" : v;
    }

    @PostConstruct
    void warn() {
        if (!enabled) return;
        if (!available()) log.warn("The AIRTEL_MONEY payment provider is switched on but not usable: it needs the client id, client secret and an https base URL.");
        else if (baseUrl.contains("openapiuat")) log.warn("AIRTEL_MONEY uses Airtel's test (UAT) system: no real money moves. Set the live base URL and live keys for a live shop.");
    }

    // ------------------------------------------------------------------ the interface

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Airtel Money";
    }

    @Override
    public boolean available() {
        if (!enabled || clientId.isEmpty() || clientSecret.isEmpty()) return false;
        // The keys travel in the requests: only https, except a server on this machine (tests).
        return baseUrl.startsWith("https://") || baseUrl.startsWith("http://127.0.0.1") || baseUrl.startsWith("http://localhost");
    }

    @Override
    public List<String> methods() {
        return List.of("MOBILE_MONEY");
    }

    @Override
    public Initiated initiate(PaymentRequest r) {
        String national = national(r.phone(), countryCode);
        if (national.length() < 7) throw ApiException.badRequest(I18n.t("Enter the mobile money number to pay from"));
        String currency = currencyOverride.isEmpty() ? r.currency() : currencyOverride;
        ObjectNode body = json.createObjectNode();
        String note = r.description() == null ? "Order" : r.description().length() > 60 ? r.description().substring(0, 60) : r.description();
        body.put("reference", note);
        ObjectNode subscriber = body.putObject("subscriber");
        subscriber.put("country", country);
        subscriber.put("currency", currency);
        subscriber.put("msisdn", national);
        ObjectNode tx = body.putObject("transaction");
        tx.put("amount", new BigDecimal(amount(r.amount())));
        tx.put("country", country);
        tx.put("currency", currency);
        tx.put("id", r.reference());
        Resp resp = call("POST", pushPath, currency, body.toString());
        JsonNode n = parse(resp.body);
        boolean ok = n.path("status").path("success").asBoolean(false);
        if ((resp.status == 200 || resp.status == 202) && ok) return new Initiated(r.reference(), null, null);
        String code = n.path("status").path("response_code").asText(n.path("status").path("code").asText(""));
        log.warn("Airtel Money refused the payment request {} ({}): HTTP {} {}", r.reference(), mask(national), resp.status, code);
        // DP00800001006 is Airtel's "invalid MSISDN"
        if ("DP00800001006".equals(code)) throw ApiException.badRequest(I18n.t("That number has no Airtel Money account. Check it or choose another way to pay."));
        throw new IllegalStateException("Airtel Money answered HTTP " + resp.status + (code.isEmpty() ? "" : " " + code));
    }

    @Override
    public StatusResult status(String reference) {
        Remote r = fetch(reference);
        if (r == null) return new StatusResult(State.PENDING, null, null, null, null);
        return new StatusResult(r.state, null, null, r.airtelId, r.state == State.FAILED ? r.message : null);
    }

    /** Airtel's refund call returns a whole payment, so refunds are done by hand; this only tells staff where to look. */
    @Override
    public RefundResult refund(String reference, BigDecimal amount, String reason) {
        String airtelId = null;
        try {
            Remote r = fetch(reference);
            if (r != null) airtelId = r.airtelId;
        } catch (RuntimeException e) {
            // the message below still applies
        }
        return new RefundResult(false, null, airtelId == null || airtelId.isBlank()
                ? I18n.t("Airtel Money refunds are done by hand: refund the customer from your Airtel Money account and record it with \"I refunded it myself\" and the Airtel reference.")
                : I18n.t("Airtel Money refunds are done by hand: refund the customer from your Airtel Money account and record it with \"I refunded it myself\" and the Airtel reference {0}.", airtelId));
    }

    /**
     * Asks Airtel instead of trusting the callback: accepted only when Airtel's own record of the payment (found by our reference) is final and
     * matches the callback's state and reference. Anything else is refused and changes nothing.
     */
    @Override
    public boolean verifySignature(String rawBody, Map<String, String> headers) {
        if (!available() || rawBody == null || rawBody.length() > 8000) return false;
        try {
            JsonNode tx = parse(rawBody).path("transaction");
            String reference = tx.path("id").asText("");
            if (!REFERENCE.matcher(reference).matches()) return false;
            // Over the limit the callback is refused; nothing is lost, the status check (about once a minute) finds the payment anyway.
            if (!allowCheck()) {
                warnFlood();
                return false;
            }
            Remote real = fetch(reference);
            if (real == null || real.state == State.PENDING) return false;
            return state(tx.path("status_code").asText("")) == real.state;
        } catch (RuntimeException e) {
            log.warn("Could not verify an Airtel Money callback: {}", e.getMessage());
            return false;
        }
    }

    private long floodWarnedAt;

    private synchronized void warnFlood() {
        long now = System.currentTimeMillis();
        if (now - floodWarnedAt < 60_000) return;
        floodWarnedAt = now;
        log.warn("AIRTEL_MONEY: more than {} callbacks in a minute, the extra ones are not checked (the status check still finds real payments)", CHECKS_PER_MINUTE);
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
            JsonNode tx = parse(rawBody).path("transaction");
            String reference = tx.path("id").asText(null);
            if (reference == null) throw new IllegalArgumentException("no transaction id");
            State s = state(tx.path("status_code").asText(""));
            String message = tx.path("message").asText(null);
            return new WebhookEvent(reference, s, null, null, tx.path("airtel_money_id").asText(null), s == State.FAILED ? message : null);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("The webhook body is not valid");
        }
    }

    // ------------------------------------------------------------------ what Airtel says

    private record Remote(State state, String airtelId, String message) {
    }

    /** TS = successful, TF = failed; TA (ambiguous), TIP (in progress) and anything else are still pending. */
    private static State state(String s) {
        String v = s == null ? "" : s.trim().toUpperCase();
        return switch (v) {
            case "TS" -> State.PAID;
            case "TF" -> State.FAILED;
            default -> State.PENDING;
        };
    }

    /** Null when Airtel does not know the id (404). Throws when Airtel cannot be reached or answers something unexpected. */
    private Remote fetch(String reference) {
        Resp resp = call("GET", statusPath + reference, null, null);
        if (resp.status == 404) return null;
        if (resp.status != 200) throw new IllegalStateException("Airtel Money answered HTTP " + resp.status + " for a status check");
        JsonNode n = parse(resp.body);
        // The answer has the transaction under "data" (the documented shape); some versions put it at the top.
        JsonNode tx = n.path("data").path("transaction");
        if (tx.isMissingNode()) tx = n.path("transaction");
        if (tx.isMissingNode() || tx.path("status").asText("").isEmpty()) return null;
        return new Remote(state(tx.path("status").asText("")), tx.path("airtel_money_id").asText(null), tx.path("message").asText(null));
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException e) {
            throw new IllegalStateException("Airtel Money answered something that is not JSON");
        }
    }

    // ------------------------------------------------------------------ the HTTP calls

    private record Resp(int status, String body) {
    }

    /** One call with the token; a 401 gets one fresh token and one retry. */
    private Resp call(String method, String path, String currency, String body) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String bearer = token(attempt > 0);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
                    .header("Authorization", "Bearer " + bearer).header("Accept", "*/*")
                    .header("X-Country", country).header("X-Currency", currency == null ? defaultCurrency() : currency);
            if (body != null) b.header("Content-Type", "application/json");
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            Resp r = send(b.build());
            if (r.status != 401 || attempt > 0) return r;
        }
        throw new IllegalStateException("unreachable");
    }

    /** Status checks carry no amount, but Airtel still wants a currency header: the one set, else the usual currency of the country. */
    private String defaultCurrency() {
        if (!currencyOverride.isEmpty()) return currencyOverride;
        return switch (country) {
            case "RW" -> "RWF";
            case "UG" -> "UGX";
            case "KE" -> "KES";
            case "TZ" -> "TZS";
            case "ZM" -> "ZMW";
            case "MW" -> "MWK";
            case "CD" -> "CDF";
            case "TD", "GA", "CG" -> "XAF";
            case "NG" -> "NGN";
            case "NE" -> "XOF";
            default -> "";
        };
    }

    private synchronized String token(boolean force) {
        if (!force && token != null && Instant.now().isBefore(tokenUntil)) return token;
        ObjectNode body = json.createObjectNode();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("grant_type", "client_credentials");
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/oauth2/token")).timeout(timeout)
                .header("Content-Type", "application/json").header("Accept", "*/*")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        Resp r = send(req);
        if (r.status != 200) throw new IllegalStateException("Airtel Money refused to give an access token: HTTP " + r.status);
        JsonNode n = parse(r.body);
        String t = n.path("access_token").asText("");
        if (t.isEmpty()) throw new IllegalStateException("Airtel Money gave no access token");
        long seconds = n.path("expires_in").asLong(3600);
        token = t;
        tokenUntil = Instant.now().plusSeconds(Math.max(30, seconds - 60));
        return t;
    }

    private Resp send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resp(res.statusCode(), res.body() == null ? "" : res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The call to Airtel Money was interrupted");
        } catch (IOException e) {
            throw new IllegalStateException("Airtel Money could not be reached: " + e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ small helpers

    /** Airtel wants the number without the country code: 0788 123 456, +250 788 123 456 and 250788123456 all become 788123456. */
    static String national(String phone, String countryCode) {
        String d = phone == null ? "" : phone.replaceAll("\\D", "");
        if (d.startsWith("00")) d = d.substring(2);
        if (d.startsWith(countryCode) && d.length() > countryCode.length() + 6) return d.substring(countryCode.length());
        if (d.startsWith("0")) return d.substring(1);
        return d;
    }

    /** No trailing zeros: 1000, not 1.0E+3 or 1000.00 (RWF has no decimals). */
    static String amount(BigDecimal a) {
        BigDecimal v = a.stripTrailingZeros();
        return (v.scale() < 0 ? v.setScale(0) : v).toPlainString();
    }

    private static String mask(String number) {
        return number == null || number.length() < 6 ? "***" : number.substring(0, 3) + "***" + number.substring(number.length() - 2);
    }
}
