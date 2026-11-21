package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.ReceiveRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * A delivery driver without a staff login (v1.59.0). Staff give each driver a private link and a 4-digit PIN; the driver opens the link on his phone,
 * types the PIN, sees only the orders assigned to him that are out for delivery, and confirms each one with the customer's delivery code (the
 * customer signs only on his own page). Failed and partial deliveries stay with staff. The link can be reset or switched off at any time.
 */
@Service
public class OnlineDriverAccessService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long HOUR = 3_600_000L;
    /** A session lasts this long after the driver last used it. */
    private static final long SESSION_SECONDS = 14L * 3600;
    private static final int PIN_TRIES = 5;
    private static final long LOCK_SECONDS = 1800;

    private final OnlineDriverRepository drivers;
    private final OnlineOrderRepository orders;
    private final OnlineOrderService orderService;
    private final OnlineStoreService storeService;
    private final PasswordEncoder encoder;
    private final AuditLogService auditLogService;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    private OnlineReturnService returnService;

    public OnlineDriverAccessService(OnlineDriverRepository drivers, OnlineOrderRepository orders, OnlineOrderService orderService,
                                     OnlineStoreService storeService, PasswordEncoder encoder, AuditLogService auditLogService) {
        this.drivers = drivers;
        this.orders = orders;
        this.orderService = orderService;
        this.storeService = storeService;
        this.encoder = encoder;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ shapes

    /** What staff get: the link, and the PIN only at the moment it is made (it is kept hashed, nobody can read it later). */
    public record DriverAccess(boolean accessOn, String url, String path, String pin, Instant lastUsedAt) {
    }

    public record DriverLinkInfo(String shopName) {
    }

    public record PinRequest(@NotNull @Size(min = 4, max = 4) String pin) {
    }

    public record DriverSession(String session, String driverName, Instant expiresAt) {
    }

    public record DriverLine(String name, BigDecimal quantity) {
    }

    /** One delivery of the driver. The delivery code is never in it. */
    public record DriverJob(Long id, String code, String customerName, String customerPhone, String customerAddress, String zoneName, String notes,
                            boolean paid, BigDecimal total, BigDecimal toCollect, int deliveryAttempts, List<DriverLine> lines,
                            String slotDate, String slotStart, String slotEnd) {
    }

    /** One order of the driver's cash: what he took at the door and, once staff settled it, what he handed in. */
    public record DriverCashLine(String code, String customerName, Instant at, BigDecimal collected, BigDecimal handedIn, BigDecimal difference) {
    }

    /** The cash the driver holds: orders paid in cash at the door and not handed in yet, with the total, and his last settlements. */
    public record DriverCash(BigDecimal holding, List<DriverCashLine> waiting, List<DriverCashLine> settled) {
    }

    /** One return the driver collects at the customer's address. The return code is never in it. */
    public record DriverReturnJob(Long id, String code, String orderCode, String customerName, String customerPhone, String customerAddress, String zoneName,
                                  String reason, List<DriverLine> lines) {
    }

    public record DriverBoard(String driverName, String shopName, String shopPhone, List<DriverJob> jobs, DriverCash cash, List<DriverReturnJob> returnJobs) {
    }

    public record DriverReceive(@Size(max = 12) String code) {
    }

    // ------------------------------------------------------------------ staff

    private OnlineDriver driver(Long id) {
        return drivers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Driver not found")));
    }

    private static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String newPin() {
        return String.format("%04d", RANDOM.nextInt(10000));
    }

    private static String hash(String v) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DriverAccess access(OnlineDriver d, String pin) {
        if (d.getAccessToken() == null) return new DriverAccess(false, null, null, null, null);
        String path = "/driver/" + d.getAccessToken();
        String base = OnlineMessages.baseUrl();
        return new DriverAccess(true, base.isEmpty() ? null : base + path, path, pin, d.getAccessLastUsedAt());
    }

    /** Makes a new link and PIN (the old link stops working, and so does a session open on the driver's phone). */
    @Transactional
    public DriverAccess create(Long id) {
        OnlineDriver d = driver(id);
        if (!d.isActive()) throw ApiException.badRequest(I18n.t("Switch the driver on before giving a link."));
        String pin = newPin();
        d.setAccessToken(newToken());
        d.setAccessCreatedAt(Instant.now());
        d.setAccessLastUsedAt(null);
        d.setPinHash(encoder.encode(pin));
        d.setPinFailures(0);
        d.setPinFailedAt(null);
        d.setSessionHash(null);
        d.setSessionExpiresAt(null);
        drivers.save(d);
        auditLogService.record("SHOP_DRIVER_LINK_MADE", "Made a private delivery link for the driver " + d.getName());
        return access(d, pin);
    }

    /** A new PIN on the same link (the driver must type it again; a lock after wrong PINs is cleared). */
    @Transactional
    public DriverAccess newPin(Long id) {
        OnlineDriver d = driver(id);
        if (d.getAccessToken() == null) throw ApiException.badRequest(I18n.t("This driver has no link yet."));
        String pin = newPin();
        d.setPinHash(encoder.encode(pin));
        d.setPinFailures(0);
        d.setPinFailedAt(null);
        d.setSessionHash(null);
        d.setSessionExpiresAt(null);
        drivers.save(d);
        auditLogService.record("SHOP_DRIVER_PIN_RESET", "Made a new PIN for the driver " + d.getName());
        return access(d, pin);
    }

    /** The link of the driver again (never the PIN). */
    @Transactional(readOnly = true)
    public DriverAccess link(Long id) {
        return access(driver(id), null);
    }

    @Transactional
    public DriverAccess off(Long id) {
        OnlineDriver d = driver(id);
        d.setAccessToken(null);
        d.setPinHash(null);
        d.setPinFailures(0);
        d.setPinFailedAt(null);
        d.setSessionHash(null);
        d.setSessionExpiresAt(null);
        drivers.save(d);
        auditLogService.record("SHOP_DRIVER_LINK_OFF", "Switched off the private delivery link of the driver " + d.getName());
        return access(d, null);
    }

    // ------------------------------------------------------------------ the driver

    private void throttle(String ip, String what, int perHour) {
        long now = System.currentTimeMillis();
        synchronized (byIp) {
            if (byIp.size() > 5000) byIp.values().removeIf(q -> q.isEmpty() || now - q.peekLast() > HOUR);
            Deque<Long> q = byIp.computeIfAbsent(what + ":" + (ip == null ? "?" : ip), k -> new ArrayDeque<>());
            while (!q.isEmpty() && now - q.peekFirst() > HOUR) q.pollFirst();
            if (q.size() >= perHour) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, I18n.t("Too many requests from your connection. Please try again later."));
            q.addLast(now);
        }
    }

    private OnlineDriver byLink(String token, String ip) {
        throttle(ip, "link", 120);
        OnlineDriver d = token == null || token.length() < 20 ? null : drivers.findByAccessToken(token).orElse(null);
        if (d == null || !d.isActive()) throw ApiException.notFound(I18n.t("This link is not valid any more. Ask the shop for a new one."));
        return d;
    }

    /** The link is real: the shop's name, for the PIN screen. */
    @Transactional(readOnly = true)
    public DriverLinkInfo info(String token, String ip) {
        byLink(token, ip);
        return new DriverLinkInfo(storeService.settings().getShopName());
    }

    /** The PIN: five wrong ones lock the link for half an hour. Gives a session for the next calls. */
    @Transactional(noRollbackFor = ApiException.class)
    public DriverSession login(String token, String pin, String ip) {
        throttle(ip, "pin", 30);
        OnlineDriver d = byLink(token, ip);
        Instant now = Instant.now();
        if (d.getPinFailures() >= PIN_TRIES && d.getPinFailedAt() != null && d.getPinFailedAt().plusSeconds(LOCK_SECONDS).isAfter(now)) {
            throw ApiException.badRequest(I18n.t("Too many wrong PINs. Try again in half an hour, or ask the shop for a new PIN."));
        }
        if (pin == null || d.getPinHash() == null || !encoder.matches(pin, d.getPinHash())) {
            d.setPinFailures(d.getPinFailedAt() != null && d.getPinFailedAt().plusSeconds(LOCK_SECONDS).isBefore(now) ? 1 : d.getPinFailures() + 1);
            d.setPinFailedAt(now);
            drivers.save(d);
            throw ApiException.badRequest(I18n.t("That PIN is not right."));
        }
        d.setPinFailures(0);
        d.setPinFailedAt(null);
        String session = newToken();
        d.setSessionHash(hash(session));
        d.setSessionExpiresAt(now.plusSeconds(SESSION_SECONDS));
        d.setAccessLastUsedAt(now);
        drivers.save(d);
        return new DriverSession(session, d.getName(), d.getSessionExpiresAt());
    }

    /** The driver behind a link and session, or a refusal that sends the phone back to the PIN screen (403, never 401, which would log staff out). */
    private OnlineDriver signedIn(String token, String session, String ip) {
        OnlineDriver d = byLink(token, ip);
        Instant now = Instant.now();
        if (session == null || session.isBlank() || d.getSessionHash() == null || d.getSessionExpiresAt() == null || d.getSessionExpiresAt().isBefore(now)
                || !MessageDigest.isEqual(hash(session).getBytes(StandardCharsets.UTF_8), d.getSessionHash().getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.forbidden(I18n.t("Type your PIN to continue."));
        }
        if (d.getAccessLastUsedAt() == null || d.getAccessLastUsedAt().plusSeconds(60).isBefore(now)) {
            d.setAccessLastUsedAt(now);
            d.setSessionExpiresAt(now.plusSeconds(SESSION_SECONDS));
            drivers.save(d);
        }
        return d;
    }

    private DriverBoard board(OnlineDriver d) {
        List<DriverJob> jobs = orders.findByStatusOrderByCreatedAtAsc("OUT_FOR_DELIVERY").stream()
                .filter(o -> d.getId().equals(o.getDriverId()) && "DELIVERY".equals(o.getDeliveryMethod()))
                .map(o -> new DriverJob(o.getId(), o.getCode(), o.getCustomerName(), o.getCustomerPhone(), o.getCustomerAddress(), o.getZoneName(), o.getNotes(),
                        "PAID".equals(o.getPaymentStatus()), o.getTotal(), OnlineDeliveryService.toCollect(o), o.getDeliveryAttempts(),
                        o.getLines().stream().map(l -> new DriverLine(l.getName(), l.getQuantity())).toList(),
                        OnlineSlotService.d(o.getSlotDate()), OnlineSlotService.t(o.getSlotStart()), OnlineSlotService.t(o.getSlotEnd())))
                .toList();
        OnlineStoreSettings s = storeService.settings();
        List<DriverReturnJob> back = returnService.toCollect(d.getId()).stream().map(r -> new DriverReturnJob(r.getId(), r.getCode(), r.getOnlineOrder().getCode(),
                r.getOnlineOrder().getCustomerName(), r.getOnlineOrder().getCustomerPhone(), r.getOnlineOrder().getCustomerAddress(), r.getOnlineOrder().getZoneName(), r.getReason(),
                r.getLines().stream().map(l -> new DriverLine(l.getName(), l.getQuantity())).toList())).toList();
        return new DriverBoard(d.getName(), s.getShopName(), s.getContactPhone(), jobs, cash(d), back);
    }

    /** His own cash only, never another driver's: what he holds (by the same list staff settle from) and his last ten settlements. */
    private DriverCash cash(OnlineDriver d) {
        List<DriverCashLine> waiting = orders.findByDriverIdAndCashCollectedGreaterThanAndCashSettledAtIsNullOrderByIdAsc(d.getId(), BigDecimal.ZERO).stream()
                .map(o -> new DriverCashLine(o.getCode(), o.getCustomerName(), OnlineDriverCashService.deliveredAt(o), o.getCashCollected(), null, null)).toList();
        List<DriverCashLine> settled = orders.findTop10ByDriverIdAndCashSettledAtIsNotNullOrderByCashSettledAtDesc(d.getId()).stream()
                .map(o -> new DriverCashLine(o.getCode(), o.getCustomerName(), o.getCashSettledAt(), o.getCashCollected(), o.getCashHandedIn(), o.getCashDifference())).toList();
        BigDecimal holding = waiting.stream().map(DriverCashLine::collected).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DriverCash(holding, waiting, settled);
    }

    @Transactional
    public DriverBoard jobs(String token, String session, String ip) {
        return board(signedIn(token, session, ip));
    }

    /**
     * The customer's code, given to this driver. Only his own orders that are out for delivery. A wrong code counts like on the staff
     * screen (five, then half an hour); partial deliveries and failed visits are not offered here.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public DriverBoard receive(String token, String session, Long orderId, DriverReceive in, String ip) {
        OnlineDriver d = signedIn(token, session, ip);
        throttle(ip, "receive", 200);
        orderService.receiveByDriver(orderId, d, new ReceiveRequest(in == null ? null : in.code(), null, null, null, null));
        return board(d);
    }

    /** The customer's return code, given to this driver for goods he collects. Only his own approved returns; wrong codes are counted. */
    @Transactional(noRollbackFor = ApiException.class)
    public DriverBoard collect(String token, String session, Long returnId, DriverReceive in, String ip) {
        OnlineDriver d = signedIn(token, session, ip);
        throttle(ip, "collect", 200);
        returnService.collectByDriver(returnId, d, in == null ? null : in.code());
        return board(d);
    }
}
