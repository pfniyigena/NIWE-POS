package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineMessages.Msg;
import com.posapp.onlinestore.OnlineStoreDtos.PaymentInfo;
import com.posapp.onlinestore.OnlineStoreDtos.PaymentRow;
import com.posapp.onlinestore.OnlineStoreDtos.ProviderDto;
import com.posapp.onlinestore.OnlineStoreDtos.ReconSummary;
import com.posapp.onlinestore.OnlineStoreDtos.Reconciliation;
import com.posapp.onlinestore.payment.PaymentProvider;
import com.posapp.onlinestore.payment.PaymentProvider.State;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Online payments (phase 2): starts a payment with the chosen provider, settles it when the provider says so (signed webhook, or the status check
 * when the webhook did not come), and looks after the money afterwards (refunds, review, reconciliation).
 * <p>
 * The rules that keep money safe: a payment is settled once, under a row lock, so a webhook and the status check cannot both act; the amount and
 * currency the provider reports must equal what we asked for; money that arrives for an order that is already closed or already paid is never lost,
 * it is flagged as REFUND_DUE or REVIEW for a person. The order only reaches the shop's queue (PLACED) once its payment is confirmed.
 */
@Service
public class OnlinePaymentService {

    private final ShopLoyaltyService loyalty;

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);
    public static final int MAX_ATTEMPTS = 5;
    /** An order that is still not paid after this long is cancelled by the system. */
    public static final Duration ABANDON_AFTER = Duration.ofHours(24);

    private final OnlinePaymentRepository payments;
    private final OnlinePaymentWebhookRepository webhooks;
    private final OnlineOrderRepository orders;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final List<PaymentProvider> providers;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;

    public OnlinePaymentService(OnlinePaymentRepository payments, OnlinePaymentWebhookRepository webhooks, OnlineOrderRepository orders,
                                OnlineStoreSettingsRepository settingsRepository, List<PaymentProvider> providers, MailService mailService,
                                StoreSettingsService storeSettingsService, AuditLogService auditLogService, ShopLoyaltyService loyalty) {
        this.loyalty = loyalty;
        this.payments = payments;
        this.webhooks = webhooks;
        this.orders = orders;
        this.settingsRepository = settingsRepository;
        this.providers = providers;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    // ------------------------------------------------------------------ providers

    public PaymentProvider provider(String code) {
        if (code == null) return null;
        return providers.stream().filter(p -> p.code().equalsIgnoreCase(code.trim())).findFirst().orElse(null);
    }

    /** The codes chosen in the settings: one, or several separated by commas (MTN_MOMO,AIRTEL_MONEY), in the order staff gave them. */
    public List<String> codes(OnlineStoreSettings s) {
        List<String> out = new ArrayList<>();
        String raw = s == null ? null : s.getPaymentProvider();
        if (raw == null) return out;
        for (String c : raw.split(",")) {
            String v = c.trim().toUpperCase(Locale.ROOT);
            if (!v.isEmpty() && !out.contains(v)) out.add(v);
        }
        return out;
    }

    /** The providers customers can pay with right now: those chosen in the settings that are available. Empty when online payments are off. */
    public List<PaymentProvider> activeProviders(OnlineStoreSettings s) {
        List<PaymentProvider> out = new ArrayList<>();
        if (s == null || !s.isOnlinePaymentsEnabled()) return out;
        for (String c : codes(s)) {
            PaymentProvider p = provider(c);
            if (p != null && p.available() && !out.contains(p)) out.add(p);
        }
        return out;
    }

    /** The providers a customer may choose from at checkout (code, name, ways to pay). */
    public List<ProviderDto> payOptions(OnlineStoreSettings s) {
        return activeProviders(s).stream().map(p -> new ProviderDto(p.code(), p.label(), true, p.methods())).toList();
    }

    /** The provider for this payment: the one asked for, or the only one that offers the way to pay. */
    private PaymentProvider pick(List<PaymentProvider> usable, String method, String providerCode) {
        List<PaymentProvider> c = usable.stream().filter(x -> x.methods().contains(method)).toList();
        if (c.isEmpty()) throw ApiException.badRequest(I18n.t("Choose how you want to pay"));
        String want = providerCode == null ? "" : providerCode.trim();
        if (!want.isEmpty()) return c.stream().filter(x -> x.code().equalsIgnoreCase(want)).findFirst().orElseThrow(() -> ApiException.badRequest(I18n.t("Choose how you want to pay")));
        if (c.size() == 1) return c.get(0);
        throw ApiException.badRequest(I18n.t("Choose which provider to pay with"));
    }

    public List<ProviderDto> providerList() {
        return providers.stream().map(p -> new ProviderDto(p.code(), p.label(), p.available(), p.methods())).toList();
    }

    /** How a customer may pay online right now (empty = not at all). */
    public List<String> payMethods(OnlineStoreSettings s) {
        List<String> out = new ArrayList<>();
        for (PaymentProvider p : activeProviders(s)) {
            for (String m : p.methods()) if (!out.contains(m)) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------ starting a payment

    /** Starts a try to pay the order. The caller saves the order. Throws a message the customer can read when it cannot start. */
    @Transactional
    public OnlinePayment start(OnlineOrder o, String method, String phone, OnlineStoreSettings s, String providerCode) {
        if (!"AWAITING_PAYMENT".equals(o.getStatus()) || "PAID".equals(o.getPaymentStatus())) throw ApiException.badRequest(I18n.t("This order cannot be paid now."));
        List<PaymentProvider> usable = activeProviders(s);
        if (usable.isEmpty()) throw ApiException.badRequest(I18n.t("Paying online is not available right now. Please choose to pay in cash."));
        String m = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        PaymentProvider p = pick(usable, m, providerCode);
        String number = phone == null ? null : phone.trim();
        if (m.equals("MOBILE_MONEY")) {
            if (number == null || number.replaceAll("\\D", "").length() < 7 || number.replaceAll("[0-9+\\-() ]", "").length() > 0) {
                throw ApiException.badRequest(I18n.t("Enter the mobile money number to pay from"));
            }
        } else {
            number = null;
        }
        long tried = o.getId() == null ? 0 : payments.countByOnlineOrderId(o.getId());
        if (tried >= MAX_ATTEMPTS) throw ApiException.badRequest(I18n.t("Too many payment attempts for this order. Please call us."));
        // A new try replaces any earlier one still waiting; should that one still be paid, the money is not lost (see apply).
        if (o.getId() != null) {
            for (OnlinePayment old : payments.findByOnlineOrderIdOrderByIdDesc(o.getId())) {
                if (old.getStatus().equals("PENDING")) old.setStatus("EXPIRED");
            }
        }
        Instant now = Instant.now();
        OnlinePayment pay = new OnlinePayment();
        pay.setOnlineOrder(o);
        pay.setProvider(p.code());
        pay.setReference("OS" + o.getCode() + "-" + (tried + 1));
        pay.setMethod(m);
        pay.setPhone(number);
        pay.setAmount(o.getTotal());
        pay.setCurrency(currency());
        pay.setExpiresAt(now.plus(Duration.ofMinutes(Math.max(5, s.getPaymentExpiryMinutes()))));
        PaymentProvider.Initiated init;
        try {
            init = p.initiate(new PaymentProvider.PaymentRequest(pay.getReference(), pay.getAmount(), pay.getCurrency(), m, number,
                    "Order " + o.getCode(), o.getCustomerName(), o.getCustomerEmail(), null));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Payment provider {} could not start {}: {}", p.code(), pay.getReference(), e.getMessage());
            throw ApiException.badRequest(I18n.t("We could not start the payment. Please try again in a moment."));
        }
        pay.setProviderReference(init.providerReference());
        pay.setRedirectUrl(init.redirectUrl());
        o.setPaymentMethod("ONLINE");
        o.setPaymentStatus("AWAITING");
        o.setPaymentChannel(m);
        if (o.getId() != null) event(o, o.getStatus(), I18n.t("Payment started ({0})", m.equals("CARD") ? I18n.t("card") : I18n.t("mobile money")), "customer");
        return payments.save(pay);
    }

    private String currency() {
        String c = storeSettingsService.getSettingsEntity().getCurrencyCode();
        return c == null || c.isBlank() ? "RWF" : c.trim().toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ settling

    /** The provider called the webhook. Wrong signature = 401 and nothing else; a right one is always answered with ok, whatever we did with it. */
    @Transactional
    public String processWebhook(String providerCode, String rawBody, Map<String, String> headers, String ip) {
        PaymentProvider p = provider(providerCode);
        if (p == null || !p.available()) throw ApiException.notFound(I18n.t("Not found."));
        if (!p.verifySignature(rawBody, headers)) {
            log.warn("Webhook for provider {} from {} had a wrong signature and was ignored", p.code(), ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, I18n.t("Wrong signature"));
        }
        String outcome;
        String reference = null;
        try {
            PaymentProvider.WebhookEvent ev = p.parseWebhook(rawBody);
            reference = ev.reference();
            outcome = ev.reference() == null ? "BAD_BODY"
                    : apply(p, ev.reference(), ev.state(), ev.amount(), ev.currency(), ev.providerReference(), ev.reason(), "webhook");
        } catch (ApiException e) {
            outcome = "BAD_BODY";
        }
        OnlinePaymentWebhook w = new OnlinePaymentWebhook();
        w.setProvider(p.code());
        w.setReference(reference);
        w.setOutcome(outcome);
        w.setBody(rawBody == null ? null : rawBody.length() > 4000 ? rawBody.substring(0, 4000) : rawBody);
        w.setSourceIp(ip);
        webhooks.save(w);
        return outcome;
    }

    /**
     * Applies what the provider says about one payment. Safe to call twice and from two places at once. Returns what was done:
     * PAID, FAILED, DUPLICATE, REVIEW, IGNORED or UNKNOWN_REFERENCE.
     */
    @Transactional
    public String apply(PaymentProvider p, String reference, State state, BigDecimal amount, String currency, String providerRef, String reason, String source) {
        OnlinePayment pay = payments.lockByReference(reference).orElse(null);
        if (pay == null || !pay.getProvider().equals(p.code())) return "UNKNOWN_REFERENCE";
        if (state == null || state == State.PENDING) return "IGNORED";
        OnlineOrder o = pay.getOnlineOrder();
        if (state == State.FAILED) {
            if (!pay.getStatus().equals("PENDING")) return "DUPLICATE";
            pay.setStatus("FAILED");
            pay.setFailureReason(cut(reason, 200));
            if (providerRef != null) pay.setProviderReference(providerRef);
            settleOrderFailure(o, pay);
            return "FAILED";
        }
        // PAID
        if (pay.getStatus().equals("PAID") || pay.getStatus().equals("REVIEW") || pay.getStatus().equals("RESOLVED")) return "DUPLICATE";
        if (providerRef != null) pay.setProviderReference(providerRef);
        boolean wrongAmount = amount != null && amount.compareTo(pay.getAmount()) != 0;
        boolean wrongCurrency = currency != null && !currency.equalsIgnoreCase(pay.getCurrency());
        if (wrongAmount || wrongCurrency) {
            pay.setStatus("REVIEW");
            pay.setFailureReason(cut(I18n.t("The provider reported {0} {1} but {2} {3} was expected",
                    amount == null ? "?" : amount.toPlainString(), currency == null ? "" : currency, pay.getAmount().toPlainString(), pay.getCurrency()), 200));
            audit("ONLINE_PAYMENT_REVIEW", "Payment " + pay.getReference() + " needs review: amount or currency differs", o);
            return "REVIEW";
        }
        if (o.getPaymentStatus().equals("PAID") || o.getPaymentStatus().equals("REFUND_DUE") || o.getPaymentStatus().equals("REFUNDED")) {
            pay.setStatus("REVIEW");
            pay.setFailureReason(cut(I18n.t("This order was already paid ({0}). Refund this second payment by hand.", o.getPaymentReference() == null ? "-" : o.getPaymentReference()), 200));
            audit("ONLINE_PAYMENT_REVIEW", "Second payment " + pay.getReference() + " for already paid order " + o.getCode(), o);
            return "REVIEW";
        }
        Instant now = Instant.now();
        pay.setStatus("PAID");
        pay.setPaidAt(now);
        pay.setFailureReason(null);
        o.setPaymentStatus("PAID");
        o.setPaymentChannel(pay.getMethod());
        o.setPaymentReference(pay.getReference());
        o.setPaidAmount(pay.getAmount());
        o.setPaidAt(now);
        String how = pay.getMethod().equals("CARD") ? I18n.t("card") : I18n.t("mobile money");
        switch (o.getStatus()) {
            case "AWAITING_PAYMENT" -> {
                o.setStatus("PLACED");
                event(o, "PLACED", I18n.t("Paid online ({0}), reference {1}", how, pay.getReference()), "payment (" + source + ")");
                orders.save(o);
                audit("ONLINE_ORDER_PAID", "Online order " + o.getCode() + " paid " + OnlineStoreService.money(pay.getAmount()) + " (" + pay.getMethod() + ", " + pay.getReference() + ")", o);
                OnlineMessages.send(mailService, storeSettingsService, o,
                        OnlineMessages.m("We received your payment for order {0}", o.getCode()),
                        OnlineMessages.m("Thank you, {0}. We received your payment of {1} for order {2}. We will confirm the order shortly. You can follow it with this code.",
                                o.getCustomerName(), OnlineStoreService.money(pay.getAmount()), o.getCode()));
                OnlineOrder fresh = o;
                settingsRepository.findAll().stream().findFirst().ifPresent(s -> OnlineMessages.notifyShop(mailService, storeSettingsService, s, fresh));
            }
            case "CANCELLED", "REJECTED" -> {
                o.setPaymentStatus("REFUND_DUE");
                event(o, o.getStatus(), I18n.t("Payment of {0} arrived after the order was closed: refund due", OnlineStoreService.money(pay.getAmount())), "payment (" + source + ")");
                orders.save(o);
                audit("ONLINE_PAYMENT_REFUND_DUE", "Payment " + pay.getReference() + " arrived for closed order " + o.getCode() + ": refund due", o);
                OnlineMessages.send(mailService, storeSettingsService, o,
                        OnlineMessages.m("About your payment for order {0}", o.getCode()),
                        OnlineMessages.m("Your order {0} was already cancelled when your payment of {1} arrived. We will refund it.", o.getCode(), OnlineStoreService.money(pay.getAmount())));
            }
            default -> {
                event(o, o.getStatus(), I18n.t("Paid online ({0}), reference {1}", how, pay.getReference()), "payment (" + source + ")");
                orders.save(o);
            }
        }
        return "PAID";
    }

    /** The attempt failed or expired: if nothing else is pending, the order is waiting for the customer to try again. */
    private void settleOrderFailure(OnlineOrder o, OnlinePayment failed) {
        if (!o.getPaymentStatus().equals("AWAITING") || !o.getStatus().equals("AWAITING_PAYMENT")) return;
        boolean stillPending = payments.findByOnlineOrderIdOrderByIdDesc(o.getId()).stream()
                .anyMatch(x -> !x.getId().equals(failed.getId()) && x.getStatus().equals("PENDING"));
        if (stillPending) return;
        o.setPaymentStatus("FAILED");
        event(o, o.getStatus(), I18n.t("Payment not completed. The customer can try again."), "payment");
        orders.save(o);
        OnlineMessages.send(mailService, storeSettingsService, o,
                OnlineMessages.m("Your payment for order {0} did not go through", o.getCode()),
                OnlineMessages.m("Your payment for order {0} was not completed. Open the order page with your order code and try again.", o.getCode()));
    }

    // ------------------------------------------------------------------ the status check (when the webhook did not come)

    @Transactional(readOnly = true)
    public List<Long> pendingIds() {
        return payments.pendingIds();
    }

    /** Asks the provider about one pending payment; closes it when its time is up. Each payment is its own transaction, so one failure stops no other. */
    @Transactional
    public void checkOne(Long paymentId, Instant now) {
        OnlinePayment peek = payments.findById(paymentId).orElse(null);
        if (peek == null || !peek.getStatus().equals("PENDING")) return;
        OnlinePayment pay = payments.lockByReference(peek.getReference()).orElse(null);
        if (pay == null || !pay.getStatus().equals("PENDING")) return;
        PaymentProvider p = provider(pay.getProvider());
        if (p == null || !p.available()) return;
        boolean expired = !now.isBefore(pay.getExpiresAt());
        if (!expired && pay.getLastCheckedAt() != null && Duration.between(pay.getLastCheckedAt(), now).getSeconds() < 55) return;
        if (!expired && Duration.between(pay.getCreatedAt(), now).getSeconds() < 45) return;
        pay.setLastCheckedAt(now);
        PaymentProvider.StatusResult r = null;
        try {
            r = p.status(pay.getReference());
        } catch (RuntimeException e) {
            log.warn("Could not check payment {} with {}: {}", pay.getReference(), p.code(), e.getMessage());
        }
        if (r != null && r.state() != State.PENDING) {
            apply(p, pay.getReference(), r.state(), r.amount(), r.currency(), r.providerReference(), r.reason(), "status check");
            return;
        }
        if (expired) {
            pay.setStatus("EXPIRED");
            pay.setFailureReason(I18n.t("Not confirmed in time"));
            settleOrderFailure(pay.getOnlineOrder(), pay);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> staleOrderIds(Instant now) {
        return orders.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc("AWAITING_PAYMENT", now.minus(ABANDON_AFTER)).stream().map(OnlineOrder::getId).toList();
    }

    /** An order nobody paid for in a day is cancelled. A payment that still arrives later becomes a refund due (see apply). */
    @Transactional
    public void abandon(Long orderId, Instant now) {
        OnlineOrder o = orders.findById(orderId).orElse(null);
        if (o == null || !o.getStatus().equals("AWAITING_PAYMENT") || o.getCreatedAt().isAfter(now.minus(ABANDON_AFTER))) return;
        boolean pending = payments.findByOnlineOrderIdOrderByIdDesc(o.getId()).stream().anyMatch(x -> x.getStatus().equals("PENDING") && x.getExpiresAt().isAfter(now));
        if (pending) return;
        o.setStatus("CANCELLED");
        event(o, "CANCELLED", I18n.t("Not paid in time"), "system");
        loyalty.giveBackSpent(o);
        orders.save(o);
        audit("ONLINE_ORDER_ABANDONED", "Online order " + o.getCode() + " cancelled: not paid in time", o);
        OnlineMessages.send(mailService, storeSettingsService, o, OnlineMessages.m("Your order {0} is cancelled", o.getCode()),
                OnlineMessages.m("Your order {0} was cancelled because it was not paid in time.", o.getCode()));
    }

    // ------------------------------------------------------------------ closing an order that was paid

    /**
     * Call after an order moved to CANCELLED or REJECTED. Pending tries are closed, and money already received becomes a refund due.
     * Returns true when there is money to give back (so the customer's message can say so).
     */
    @Transactional
    public boolean afterClosed(OnlineOrder o) {
        if (o.getId() != null) {
            for (OnlinePayment p : payments.findByOnlineOrderIdOrderByIdDesc(o.getId())) {
                if (p.getStatus().equals("PENDING")) p.setStatus("EXPIRED");
            }
        }
        if (!"PAID".equals(o.getPaymentStatus())) return false;
        o.setPaymentStatus("REFUND_DUE");
        event(o, o.getStatus(), I18n.t("Refund due: {0}", OnlineStoreService.money(o.getPaidAmount())), user());
        audit("ONLINE_PAYMENT_REFUND_DUE", "Online order " + o.getCode() + " closed after payment: refund of " + OnlineStoreService.money(o.getPaidAmount()) + " due", o);
        return true;
    }

    // ------------------------------------------------------------------ refunds and review

    /**
     * Gives the customer's money back. manual = false asks the provider to refund; manual = true records a refund staff made themselves
     * (the provider's dashboard, mobile money, cash), which needs its reference.
     */
    @Transactional
    public OnlineOrder refund(Long orderId, boolean manual, String reference, String note) {
        OnlineOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound(I18n.t("Online order not found")));
        if (!"REFUND_DUE".equals(o.getPaymentStatus())) throw ApiException.badRequest(I18n.t("There is no refund due on this order"));
        String ref = giveBack(o, o.getPaidAmount(), manual, reference, note);
        o.setPaymentStatus("REFUNDED");
        o.setRefundedAt(Instant.now());
        o.setRefundReference(ref);
        o.setRefundedTotal(o.getPaidAmount());
        event(o, o.getStatus(), I18n.t("Refunded {0}, reference {1}", OnlineStoreService.money(o.getPaidAmount()), ref) + (note == null || note.isBlank() ? "" : ": " + note.trim()), user());
        orders.save(o);
        audit("ONLINE_PAYMENT_REFUNDED", "Refunded online order " + o.getCode() + " " + OnlineStoreService.money(o.getPaidAmount()) + " (" + ref + ")", o);
        OnlineMessages.send(mailService, storeSettingsService, o, OnlineMessages.m("Your refund for order {0}", o.getCode()),
                OnlineMessages.m("We refunded {0} for your order {1}. Reference: {2}. It may take a few days to show on your account.",
                        OnlineStoreService.money(o.getPaidAmount()), o.getCode(), ref));
        return o;
    }

    /**
     * Gives back part of what the customer paid online, for a return of some of the goods (see OnlineReturnService). The order stays as it is, with the
     * total given back so far noted on it; once everything that was paid is back, the payment becomes REFUNDED. Returns the refund reference. The
     * customer is not told here: the return's own message does that.
     */
    @Transactional
    public String refundPart(OnlineOrder o, BigDecimal amount, boolean manual, String reference, String note) {
        if (!"PAID".equals(o.getPaymentStatus()) || o.getPaidAmount() == null) throw ApiException.badRequest(I18n.t("There is nothing left to refund on this order"));
        BigDecimal left = o.getPaidAmount().subtract(o.getRefundedTotal() == null ? BigDecimal.ZERO : o.getRefundedTotal());
        if (amount == null || amount.signum() <= 0 || amount.compareTo(left) > 0) throw ApiException.badRequest(I18n.t("There is nothing left to refund on this order"));
        String ref = giveBack(o, amount, manual, reference, note);
        BigDecimal total = (o.getRefundedTotal() == null ? BigDecimal.ZERO : o.getRefundedTotal()).add(amount);
        o.setRefundedTotal(total);
        if (total.compareTo(o.getPaidAmount()) >= 0) {
            o.setPaymentStatus("REFUNDED");
            o.setRefundedAt(Instant.now());
            o.setRefundReference(ref);
        }
        event(o, o.getStatus(), I18n.t("Refunded {0}, reference {1}", OnlineStoreService.money(amount), ref), user());
        orders.save(o);
        audit("ONLINE_PAYMENT_REFUNDED", "Refunded online order " + o.getCode() + " " + OnlineStoreService.money(amount) + " for a return (" + ref + ")", o);
        return ref;
    }

    /** The refund itself: by the provider, or the reference of one staff made by hand. Returns the reference. Throws when it cannot be done. */
    private String giveBack(OnlineOrder o, BigDecimal amount, boolean manual, String reference, String note) {
        if (manual) {
            String ref = reference == null ? "" : reference.trim();
            if (ref.length() < 3) throw ApiException.badRequest(I18n.t("Enter the reference of the refund you made (at least 3 characters)"));
            return ref;
        }
        OnlinePayment paid = payments.findByReference(o.getPaymentReference() == null ? "" : o.getPaymentReference()).orElse(null);
        PaymentProvider p = paid == null ? null : provider(paid.getProvider());
        if (p == null || !p.available()) throw ApiException.badRequest(I18n.t("The payment provider is not available. Refund by hand and enter the reference."));
        PaymentProvider.RefundResult r;
        try {
            r = p.refund(paid.getReference(), amount, note);
        } catch (RuntimeException e) {
            log.warn("Refund of {} failed with {}: {}", paid.getReference(), p.code(), e.getMessage());
            throw ApiException.badRequest(I18n.t("The provider could not refund this payment. Refund it by hand and enter the reference."));
        }
        if (!r.ok()) throw ApiException.badRequest(r.message() == null || r.message().isBlank()
                ? I18n.t("The provider could not refund this payment. Refund it by hand and enter the reference.") : r.message());
        return r.refundReference() == null ? "provider" : r.refundReference();
    }

    /** Staff looked at a payment flagged for review and dealt with it (usually refunded it by hand). */
    @Transactional
    public void resolve(Long paymentId, String note) {
        OnlinePayment p = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound(I18n.t("Payment not found")));
        if (!p.getStatus().equals("REVIEW")) throw ApiException.badRequest(I18n.t("Only a payment under review can be resolved"));
        String n = note == null ? "" : note.trim();
        if (n.length() < 3) throw ApiException.badRequest(I18n.t("Write what was done (at least 3 characters)"));
        p.setStatus("RESOLVED");
        p.setFailureReason(cut((p.getFailureReason() == null ? "" : p.getFailureReason() + " | ") + user() + ": " + n, 200));
        audit("ONLINE_PAYMENT_RESOLVED", "Payment " + p.getReference() + " resolved: " + n, p.getOnlineOrder());
    }

    // ------------------------------------------------------------------ what the customer and staff see

    /** What the customer's order page shows about payment. */
    @Transactional(readOnly = true)
    public PaymentInfo info(OnlineOrder o) {
        if (!"ONLINE".equals(o.getPaymentMethod())) return new PaymentInfo("COD", "NONE", null, null, false, null, null);
        OnlinePayment last = o.getId() == null ? null : payments.findByOnlineOrderIdOrderByIdDesc(o.getId()).stream().findFirst().orElse(null);
        boolean pending = last != null && last.getStatus().equals("PENDING");
        boolean canRetry = o.getStatus().equals("AWAITING_PAYMENT") && !o.getPaymentStatus().equals("PAID")
                && (o.getPaymentStatus().equals("FAILED") || pending) && payments.countByOnlineOrderId(o.getId()) < MAX_ATTEMPTS;
        return new PaymentInfo("ONLINE", o.getPaymentStatus(), last == null ? o.getPaymentChannel() : last.getMethod(),
                pending ? last.getRedirectUrl() : null, canRetry, pending ? last.getExpiresAt() : null, o.getPaidAmount());
    }

    @Transactional(readOnly = true)
    public Reconciliation reconciliation(Instant from, Instant to, String status) {
        Map<Long, OnlinePayment> found = new LinkedHashMap<>();
        for (OnlinePayment p : payments.between(from, to)) found.put(p.getId(), p);
        // What needs a person is shown whatever its date.
        for (OnlinePayment p : payments.needAttention(Instant.now().minus(Duration.ofMinutes(15)))) found.putIfAbsent(p.getId(), p);
        List<PaymentRow> rows = new ArrayList<>();
        BigDecimal paid = BigDecimal.ZERO, inBooks = BigDecimal.ZERO, held = BigDecimal.ZERO, due = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
        long review = 0, failed = 0, stuck = 0;
        for (OnlinePayment p : found.values().stream().sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList()) {
            OnlineOrder o = p.getOnlineOrder();
            boolean accepted = p.getStatus().equals("PAID");
            String flag = "";
            if (p.getStatus().equals("REVIEW")) { flag = "REVIEW"; review++; }
            else if (accepted && o.getPaymentStatus().equals("REFUND_DUE")) flag = "REFUND_DUE";
            else if (p.getStatus().equals("PENDING") && p.getExpiresAt().isBefore(Instant.now().minus(Duration.ofMinutes(15)))) { flag = "STUCK_PENDING"; stuck++; }
            if (p.getStatus().equals("FAILED") || p.getStatus().equals("EXPIRED")) failed++;
            if (accepted && between(p.getCreatedAt(), from, to)) {
                paid = paid.add(p.getAmount());
                switch (o.getPaymentStatus()) {
                    case "REFUND_DUE" -> due = due.add(p.getAmount());
                    case "REFUNDED" -> refunded = refunded.add(p.getAmount());
                    default -> {
                        // Goods returned after completion: that part was given back, the rest stays in the books.
                        BigDecimal part = o.getRefundedTotal() == null ? BigDecimal.ZERO : o.getRefundedTotal().min(p.getAmount());
                        refunded = refunded.add(part);
                        if (o.getStatus().equals("COMPLETED")) inBooks = inBooks.add(p.getAmount().subtract(part));
                        else held = held.add(p.getAmount());
                    }
                }
            }
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(p.getStatus()) && !status.equalsIgnoreCase(flag)) continue;
            rows.add(new PaymentRow(p.getId(), o.getId(), o.getCode(), p.getCreatedAt(), p.getProvider(), p.getReference(), p.getProviderReference(), p.getMethod(),
                    p.getPhone(), p.getAmount(), p.getCurrency(), p.getStatus(), p.getFailureReason(), o.getStatus(), o.getPaymentStatus(), flag));
        }
        return new Reconciliation(new ReconSummary(paid, inBooks, held, due, refunded, review, failed, stuck), rows);
    }

    private static boolean between(Instant t, Instant from, Instant to) {
        return !t.isBefore(from) && t.isBefore(to);
    }

    /** What the staff screens badge: money waiting to be given back and payments that need a person. */
    @Transactional(readOnly = true)
    public Map<String, Long> attentionCounts() {
        long awaiting = orders.countByStatus("AWAITING_PAYMENT");
        long due = orders.countByPaymentStatus("REFUND_DUE");
        long review = payments.countByStatus("REVIEW");
        return Map.of("awaitingPayment", awaiting, "refundsDue", due, "paymentsToReview", review);
    }

    // ------------------------------------------------------------------ helpers

    private void event(OnlineOrder o, String status, String note, String by) {
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(status);
        ev.setNote(cut(note, 200));
        ev.setByUser(by);
        o.getEvents().add(ev);
    }

    private void audit(String action, String description, OnlineOrder o) {
        auditLogService.record(action, description, "OnlineOrder", o.getId());
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
