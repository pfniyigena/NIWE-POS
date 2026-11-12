package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Customer accounts on the public shop (phase 3). A customer proves an e-mail address with a one-time code and gets a session; there are no passwords.
 * The shop works without an account: an account only adds order history, a saved address and one-click reorder. Guest orders placed with the same
 * e-mail address join the account when the address is proved.
 * <p>
 * Safe by construction: only hashes of codes and tokens are stored; a code lives 10 minutes and dies after 5 wrong tries; codes and attempts are limited per
 * address and per connection; asking for a code never tells whether an account exists; every account call is scoped to the signed-in customer.
 */
@Service
public class ShopAccountService {

    private static final Logger log = LoggerFactory.getLogger(ShopAccountService.class);
    static final Duration CODE_LIFE = Duration.ofMinutes(10);
    static final Duration SESSION_LIFE = Duration.ofDays(30);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String WRONG_CODE = "That code is not right or has expired. Ask for a new one.";

    private final ShopCustomerRepository customers;
    private final ShopLoginCodeRepository codes;
    private final ShopSessionRepository sessions;
    private final OnlineOrderRepository orders;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final OnlineListingRepository listingRepository;
    private final OnlineStoreService storeService;
    private final List<ShopCodeChannel> channels;
    private final AuditLogService auditLogService;
    private final String devCode;
    private final Environment environment;

    /** Requests per key in the last hour (connection or address), to slow down scripts that guess codes or flood mailboxes. */
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public ShopAccountService(ShopCustomerRepository customers, ShopLoginCodeRepository codes, ShopSessionRepository sessions, OnlineOrderRepository orders,
                              OnlineStoreSettingsRepository settingsRepository, OnlineListingRepository listingRepository, OnlineStoreService storeService,
                              List<ShopCodeChannel> channels, AuditLogService auditLogService,
                              @Value("${app.online-store.accounts.dev-code:}") String devCode, Environment environment) {
        this.customers = customers;
        this.codes = codes;
        this.sessions = sessions;
        this.orders = orders;
        this.settingsRepository = settingsRepository;
        this.listingRepository = listingRepository;
        this.storeService = storeService;
        this.channels = channels;
        this.auditLogService = auditLogService;
        this.devCode = devCode == null ? "" : devCode.trim();
        this.environment = environment;
    }

    @PostConstruct
    void warn() {
        if (devCode.isEmpty()) return;
        boolean dev = List.of(environment.getActiveProfiles()).contains("dev");
        log.warn("app.online-store.accounts.dev-code is set: EVERY sign-in code is '{}'. For development and tests only{}.", devCode,
                dev ? "" : " - and this is NOT the dev profile: remove it now, anyone can sign in to any account");
    }

    // ------------------------------------------------------------------ helpers

    private OnlineStoreSettings open() {
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (s == null || !s.isEnabled() || !s.isAccountsEnabled()) throw ApiException.notFound(I18n.t("Accounts are not available."));
        return s;
    }

    private void throttle(String key, int perHour) {
        long now = System.currentTimeMillis();
        synchronized (hits) {
            if (hits.size() > 20_000) hits.clear();
            Deque<Long> d = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            while (!d.isEmpty() && now - d.peekFirst() > 3_600_000L) d.pollFirst();
            if (d.size() >= perHour) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, I18n.t("Too many requests from your connection. Please try again later."));
            d.addLast(now);
        }
    }

    private static String sha256(String s) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String codeHash(String code, String email) {
        return sha256(code + "|" + email);
    }

    static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }

    private static String lang(String l) {
        return "fr".equalsIgnoreCase(l) ? "fr" : "en";
    }

    /** The e-mail address a sign-in goes to: the address typed, or the address of the one account that has the phone number typed. Null when there is none. */
    private String resolveEmail(String identifier) {
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty()) throw ApiException.badRequest(I18n.t("Enter your e-mail address or phone number"));
        if (id.contains("@")) {
            String email = id.toLowerCase(Locale.ROOT);
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 160) throw ApiException.badRequest(I18n.t("That e-mail address does not look right"));
            return email;
        }
        String phone = normalizePhone(id);
        if (phone.replaceAll("\\D", "").length() < 7 || id.replaceAll("[0-9+\\-() ]", "").length() > 0) throw ApiException.badRequest(I18n.t("Enter your e-mail address or phone number"));
        List<ShopCustomer> found = customers.findByPhone(phone);
        return found.size() == 1 ? found.get(0).getEmail() : null;
    }

    // ------------------------------------------------------------------ signing in

    /**
     * Sends a sign-in code. The answer is the same whether or not an account exists, so the form cannot be used to find out who has one.
     * A phone number only works for an account that already has it saved; the code then goes to that account's e-mail address.
     */
    @Transactional
    public AccountCodeResult requestCode(AccountCodeRequest in, String ip) {
        open();
        throttle("c:" + ip, 12);
        String email = resolveEmail(in.identifier());
        if (email == null) return new AccountCodeResult(true);
        Instant now = Instant.now();
        if (codes.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, I18n.t("Too many codes were asked for this address. Please try again later."));
        }
        ShopCodeChannel channel = channels.stream().filter(c -> c.code().equals("EMAIL")).findFirst().orElse(null);
        boolean canSend = channel != null && channel.available();
        // With the development code set, no mail server is needed: the code is always that value.
        if (!canSend && devCode.isEmpty()) throw ApiException.badRequest(I18n.t("We cannot send e-mails right now. Please try again later."));
        for (ShopLoginCode old : codes.lockOpen(email)) old.setUsed(true);
        String value = devCode.isEmpty() ? String.format("%06d", RANDOM.nextInt(1_000_000)) : devCode;
        ShopLoginCode c = new ShopLoginCode();
        c.setEmail(email);
        c.setCodeHash(codeHash(value, email));
        c.setExpiresAt(now.plus(CODE_LIFE));
        codes.save(c);
        if (canSend) {
            try {
                channel.send(email, value, lang(in.language()));
            } catch (RuntimeException e) {
                log.warn("Could not send a shop sign-in code: {}", e.getMessage());
                if (devCode.isEmpty()) throw ApiException.badRequest(I18n.t("We could not send the code. Please try again later."));
            }
        }
        return new AccountCodeResult(true);
    }

    /**
     * Checks the code. A wrong code counts against the newest code of the address (the count is kept even though the answer is an error) and after 5 it is dead.
     * A right code opens the account (made on the first sign-in), joins the guest orders placed with the same e-mail address, and starts a session.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AccountSession verify(AccountVerifyRequest in, String ip) {
        open();
        throttle("v:" + ip, 40);
        String email = resolveEmail(in.identifier());
        String given = in.code() == null ? "" : in.code().replaceAll("\\s", "");
        if (email == null || given.isEmpty()) throw ApiException.badRequest(I18n.t(WRONG_CODE));
        Instant now = Instant.now();
        List<ShopLoginCode> open = codes.lockOpen(email);
        if (open.isEmpty()) throw ApiException.badRequest(I18n.t(WRONG_CODE));
        ShopLoginCode c = open.get(0);
        if (!c.getExpiresAt().isAfter(now) || c.getAttempts() >= MAX_ATTEMPTS) {
            c.setUsed(true);
            throw ApiException.badRequest(I18n.t(WRONG_CODE));
        }
        if (!MessageDigest.isEqual(codeHash(given, email).getBytes(StandardCharsets.UTF_8), c.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            c.setAttempts(c.getAttempts() + 1);
            if (c.getAttempts() >= MAX_ATTEMPTS) c.setUsed(true);
            throw ApiException.badRequest(I18n.t(WRONG_CODE));
        }
        c.setUsed(true);
        ShopCustomer customer = customers.findByEmail(email).orElseGet(() -> {
            ShopCustomer n = new ShopCustomer();
            n.setEmail(email);
            return n;
        });
        boolean created = customer.getId() == null;
        customer.setLastLoginAt(now);
        customer = customers.save(customer);
        int linked = orders.linkGuestOrders(customer.getId(), email);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        ShopSession s = new ShopSession();
        s.setShopCustomerId(customer.getId());
        s.setTokenHash(sha256(token));
        s.setExpiresAt(now.plus(SESSION_LIFE));
        s.setLastUsedAt(now);
        sessions.save(s);
        if (created) auditLogService.record("SHOP_ACCOUNT_CREATED", "A shop account was opened (" + linked + " earlier order(s) joined it)", "ShopCustomer", customer.getId());
        return new AccountSession(token, profile(customer));
    }

    /** The signed-in customer for a token, or 401. */
    @Transactional
    public ShopCustomer authenticate(String token) {
        open();
        ShopCustomer c = find(token);
        if (c == null) throw new ApiException(HttpStatus.UNAUTHORIZED, I18n.t("Please sign in again."));
        return c;
    }

    /** The id of the signed-in customer, or null: a stale or missing token must never stop a guest order. */
    @Transactional
    public Long optionalCustomerId(String token) {
        if (token == null || token.isBlank()) return null;
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (s == null || !s.isAccountsEnabled()) return null;
        ShopCustomer c = find(token);
        return c == null ? null : c.getId();
    }

    private ShopCustomer find(String token) {
        if (token == null || token.isBlank() || token.length() > 100) return null;
        ShopSession s = sessions.findByTokenHash(sha256(token.trim())).orElse(null);
        if (s == null) return null;
        Instant now = Instant.now();
        if (!s.getExpiresAt().isAfter(now)) return null;
        if (s.getLastUsedAt() == null || Duration.between(s.getLastUsedAt(), now).toMinutes() >= 5) s.setLastUsedAt(now);
        return customers.findById(s.getShopCustomerId()).orElse(null);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        sessions.findByTokenHash(sha256(token.trim())).ifPresent(sessions::delete);
    }

    // ------------------------------------------------------------------ the account

    private static AccountProfile profile(ShopCustomer c) {
        return new AccountProfile(c.getEmail(), c.getPhone(), c.getName(), c.getAddress(), c.getLanguage());
    }

    @Transactional
    public AccountProfile me(String token) {
        return profile(authenticate(token));
    }

    @Transactional
    public AccountProfile update(String token, AccountProfileRequest in) {
        ShopCustomer c = authenticate(token);
        String name = in.name() == null ? null : in.name().trim();
        String address = in.address() == null ? null : in.address().trim();
        String phone = in.phone() == null || in.phone().isBlank() ? null : in.phone().trim();
        if (phone != null) {
            if (phone.replaceAll("[0-9+\\-() ]", "").length() > 0 || phone.replaceAll("\\D", "").length() < 7) throw ApiException.badRequest(I18n.t("Enter a phone number we can call"));
            phone = normalizePhone(phone);
        }
        c.setName(name == null || name.isEmpty() ? null : name);
        c.setAddress(address == null || address.isEmpty() ? null : address);
        c.setPhone(phone);
        c.setLanguage(lang(in.language()));
        return profile(customers.save(c));
    }

    @Transactional
    public List<AccountOrder> orders(String token) {
        ShopCustomer c = authenticate(token);
        return orders.findTop50ByShopCustomerIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(o -> new AccountOrder(o.getCode(), o.getStatus(), o.getDeliveryMethod(), o.getTotal(), o.getCreatedAt(),
                        o.getLines().stream().mapToInt(l -> l.getQuantity().intValue()).sum(), o.getPaymentStatus())).toList();
    }

    /** The products of an earlier order as they are on the shop now (current price and stock); what is gone or sold out is listed apart. Prices are never copied from the old order. */
    @Transactional
    public Reorder reorder(String token, String code) {
        ShopCustomer c = authenticate(token);
        OnlineOrder o = orders.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT)).filter(x -> c.getId().equals(x.getShopCustomerId()))
                .orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        OnlineStoreSettings s = settingsRepository.findAll().get(0);
        List<ReorderLine> lines = new ArrayList<>();
        List<String> gone = new ArrayList<>();
        for (OnlineOrderLine l : o.getLines()) {
            OnlineListing listing = listingRepository.findByProductId(l.getProductId()).filter(OnlineListing::isListed).filter(x -> x.getProduct().isActive()).orElse(null);
            PublicProduct p = listing == null ? null : storeService.publicProduct(listing, s);
            if (p == null || "SOLD_OUT".equals(p.stock())) {
                gone.add(l.getName());
                continue;
            }
            lines.add(new ReorderLine(p, l.getQuantity().min(BigDecimal.valueOf(20))));
        }
        return new Reorder(lines, gone);
    }

    /** Deletes the account and its sign-ins. The orders stay (the shop needs them for its books) but are no longer tied to it. */
    @Transactional
    public void deleteAccount(String token) {
        ShopCustomer c = authenticate(token);
        orders.unlinkOrders(c.getId());
        sessions.deleteForCustomer(c.getId());
        customers.delete(c);
        auditLogService.record("SHOP_ACCOUNT_DELETED", "A shop account was deleted by its owner", "ShopCustomer", c.getId());
    }

    /** Old codes and finished sessions are removed. */
    @Transactional
    public int purge(Instant now) {
        int n = sessions.deleteExpired(now);
        n += codes.deleteOlderThan(now.minus(Duration.ofDays(1)));
        return n;
    }
}
