package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.common.TaxMath;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.salesorder.StockReservationService;
import com.posapp.settings.StoreSettingsService;
import com.posapp.tax.TaxRate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a stranger on the public shop can do: read what is on sale, place an order and follow it by its code. Nothing here ever reaches a bill, a
 * cost, a supplier or another customer; prices, stock and totals are always worked out here, never taken from the browser.
 */
@Service
public class OnlineStoreService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_LINES = 40;
    private static final int MAX_QUANTITY = 20;
    private static final int MAX_TOTAL_QUANTITY = 60;
    private static final int LOW_STOCK_AT = 5;
    private static final long HOUR = 3_600_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnlineStoreSettingsRepository settingsRepository;
    private final OnlineListingRepository listingRepository;
    private final OnlineDeliveryZoneRepository zoneRepository;
    private final OnlineOrderRepository orderRepository;
    private final StockReservationService stockReservationService;
    private final StoreSettingsService storeSettingsService;
    private final MailService mailService;
    private final AuditLogService auditLogService;

    /** Requests per connection in the last hour, to slow down scripts that fill the order list. */
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public OnlineStoreService(OnlineStoreSettingsRepository settingsRepository, OnlineListingRepository listingRepository,
                              OnlineDeliveryZoneRepository zoneRepository, OnlineOrderRepository orderRepository,
                              StockReservationService stockReservationService, StoreSettingsService storeSettingsService,
                              MailService mailService, AuditLogService auditLogService) {
        this.settingsRepository = settingsRepository;
        this.listingRepository = listingRepository;
        this.zoneRepository = zoneRepository;
        this.orderRepository = orderRepository;
        this.stockReservationService = stockReservationService;
        this.storeSettingsService = storeSettingsService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ shared helpers

    /** Read-write on purpose: the first call on a new database creates the row. */
    @Transactional
    public OnlineStoreSettings settings() {
        List<OnlineStoreSettings> all = settingsRepository.findAll();
        return all.isEmpty() ? settingsRepository.save(new OnlineStoreSettings()) : all.get(0);
    }

    /** True when the shop is switched on in the Online Store settings. */
    @Transactional
    public boolean isOpen() {
        return settings().isEnabled();
    }

    private OnlineStoreSettings openSettings() {
        OnlineStoreSettings s = settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        return s;
    }

    /** What the customer pays per unit: the shop price, plus tax when the product's tax is added on top. */
    static BigDecimal shown(Product p, BigDecimal base) {
        TaxRate tax = p.getTaxRate();
        if (tax == null || tax.isInclusive() || tax.getRatePercent() == null) return base;
        return base.add(TaxMath.taxOnAmount(base, tax.getRatePercent(), false)).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal basePrice(OnlineListing l) {
        return l.getOnlinePrice() != null ? l.getOnlinePrice() : l.getProduct().getUnitPrice();
    }

    /** Units the shop may still sell of this product, or null when stock is not tracked. */
    BigDecimal sellable(Product p, int buffer) {
        if (!p.isTrackStock() || p.isService()) return null;
        return stockReservationService.available(p).subtract(BigDecimal.valueOf(buffer));
    }

    static String stockLabel(BigDecimal sellable) {
        if (sellable == null) return "IN_STOCK";
        if (sellable.signum() <= 0) return "SOLD_OUT";
        return sellable.compareTo(BigDecimal.valueOf(LOW_STOCK_AT)) <= 0 ? "LOW" : "IN_STOCK";
    }

    static String money(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return x.toPlainString();
    }

    private static String clean(String v, int max, String what, boolean required) {
        String t = v == null ? "" : v.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            if (required) throw ApiException.badRequest(what + I18n.t(" is required"));
            return null;
        }
        if (t.length() > max) throw ApiException.badRequest(what + I18n.t(" is too long"));
        return t;
    }

    private static String newCode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) b.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return b.toString();
    }

    private void throttle(String ip, int perHour) {
        long now = System.currentTimeMillis();
        synchronized (byIp) {
            if (byIp.size() > 5000) byIp.values().removeIf(d -> d.isEmpty() || now - d.peekLast() > HOUR);
            Deque<Long> d = byIp.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
            while (!d.isEmpty() && now - d.peekFirst() > HOUR) d.pollFirst();
            if (d.size() >= perHour) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, I18n.t("Too many requests from your connection. Please try again later."));
            d.addLast(now);
        }
    }

    // ------------------------------------------------------------------ the public shop

    @Transactional
    public PublicShop shop() {
        OnlineStoreSettings s = openSettings();
        String name = s.getShopName() != null && !s.getShopName().isBlank() ? s.getShopName() : storeSettingsService.getSettingsEntity().getBusinessName();
        List<PublicZone> zones = zoneRepository.findAllByOrderBySortOrderAscNameAsc().stream().filter(OnlineDeliveryZone::isActive)
                .map(z -> new PublicZone(z.getId(), z.getName(), z.getFee(), z.getMinOrder())).toList();
        List<PublicProduct> products = new ArrayList<>();
        for (OnlineListing l : listingRepository.findByListedTrueOrderBySortOrderAscIdAsc()) {
            Product p = l.getProduct();
            if (!p.isActive()) continue;
            String title = l.getWebTitle() != null && !l.getWebTitle().isBlank() ? l.getWebTitle() : p.getName();
            String description = l.getWebDescription() != null && !l.getWebDescription().isBlank() ? l.getWebDescription() : p.getDescription();
            products.add(new PublicProduct(p.getId(), title, description, shown(p, basePrice(l)),
                    p.getCategory() == null ? null : p.getCategory().getName(),
                    p.getIconDataUrl() == null || p.getIconDataUrl().isBlank() ? null : "/api/public/store/products/" + p.getId() + "/image",
                    stockLabel(sellable(p, s.getStockBuffer()))));
        }
        boolean open = !s.isPaused();
        return new PublicShop(name, s.getIntroText(), s.getContactPhone(), s.getPickupAddress(), open, open ? null : s.getPauseMessage(),
                s.isPickupEnabled(), s.isDeliveryEnabled(), s.isCodEnabled(), s.getMinOrder(), s.getFreeDeliveryOver(), zones, products);
    }

    /** The product's own picture as bytes; the shop page asks for it per product so the product list stays small. */
    @Transactional(readOnly = true)
    public Image image(Long productId) {
        OnlineListing l = listingRepository.findByProductId(productId).filter(OnlineListing::isListed).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        String url = l.getProduct().getIconDataUrl();
        if (url == null || !url.startsWith("data:") || !url.contains(";base64,")) throw ApiException.notFound(I18n.t("Not found."));
        String type = url.substring(5, url.indexOf(';'));
        if (!type.startsWith("image/")) throw ApiException.notFound(I18n.t("Not found."));
        try {
            return new Image(type, Base64.getDecoder().decode(url.substring(url.indexOf(";base64,") + 8)));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound(I18n.t("Not found."));
        }
    }

    public record Image(String contentType, byte[] bytes) {
    }

    // ------------------------------------------------------------------ placing an order

    @Transactional
    public Track place(PlaceRequest in, String ip) {
        OnlineStoreSettings s = openSettings();
        if (in.website() != null && !in.website().isBlank()) throw ApiException.badRequest(I18n.t("The order could not be placed"));
        if (s.isPaused()) {
            throw ApiException.badRequest(I18n.t("We are not taking orders right now.") + (s.getPauseMessage() == null ? "" : " " + s.getPauseMessage()));
        }
        throttle(ip, s.getMaxPerIpPerHour());
        if (!s.isCodEnabled()) throw ApiException.badRequest(I18n.t("Paying online is not available yet. Please try again later."));
        String method = in.method() == null ? "" : in.method().trim().toUpperCase();
        if (!method.equals("DELIVERY") && !method.equals("PICKUP")) throw ApiException.badRequest(I18n.t("Choose delivery or pick-up"));
        if (method.equals("DELIVERY") && !s.isDeliveryEnabled()) throw ApiException.badRequest(I18n.t("Delivery is not available right now"));
        if (method.equals("PICKUP") && !s.isPickupEnabled()) throw ApiException.badRequest(I18n.t("Pick-up is not available right now"));

        String name = clean(in.name(), 80, I18n.t("Your name"), true);
        String phone = clean(in.phone(), 40, I18n.t("Your phone number"), true);
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7 || phone.replaceAll("[0-9+\\-() ]", "").length() > 0) throw ApiException.badRequest(I18n.t("Enter a phone number we can call"));
        String email = clean(in.email(), 160, I18n.t("Your e-mail address"), false);
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest(I18n.t("That e-mail address does not look right"));
        if (orderRepository.countByCustomerPhoneAndStatus(phone, "PLACED") >= s.getMaxPendingPerPhone()) {
            throw ApiException.badRequest(I18n.t("You already have {0} orders waiting for an answer. Please wait for them or call us.", s.getMaxPendingPerPhone()));
        }

        String address = null;
        OnlineDeliveryZone zone = null;
        if (method.equals("DELIVERY")) {
            address = clean(in.address(), 240, I18n.t("The delivery address"), true);
            List<OnlineDeliveryZone> zones = zoneRepository.findAllByOrderBySortOrderAscNameAsc().stream().filter(OnlineDeliveryZone::isActive).toList();
            if (!zones.isEmpty()) {
                if (in.zoneId() == null) throw ApiException.badRequest(I18n.t("Choose your delivery area"));
                zone = zones.stream().filter(z -> z.getId().equals(in.zoneId())).findFirst().orElseThrow(() -> ApiException.badRequest(I18n.t("We do not deliver to that area")));
            }
        }

        List<PublicLine> lines = in.lines() == null ? List.of() : in.lines();
        if (lines.isEmpty()) throw ApiException.badRequest(I18n.t("Add something to your order first"));
        if (lines.size() > MAX_LINES) throw ApiException.badRequest(I18n.t("That is too many different products for one order"));
        // One line per product, however many times the browser sent it.
        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        for (PublicLine l : lines) {
            BigDecimal q = l.quantity();
            if (q == null || q.stripTrailingZeros().scale() > 0 || q.compareTo(BigDecimal.ONE) < 0 || q.compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) {
                throw ApiException.badRequest(I18n.t("Quantities must be whole numbers between 1 and {0}", MAX_QUANTITY));
            }
            totalQty = totalQty.add(q);
            wanted.merge(l.productId(), q, BigDecimal::add);
        }
        if (totalQty.compareTo(BigDecimal.valueOf(MAX_TOTAL_QUANTITY)) > 0) throw ApiException.badRequest(I18n.t("That is too many items for one order"));

        OnlineOrder o = new OnlineOrder();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : wanted.entrySet()) {
            OnlineListing l = listingRepository.findByProductId(e.getKey()).filter(OnlineListing::isListed)
                    .filter(x -> x.getProduct().isActive()).orElseThrow(() -> ApiException.badRequest(I18n.t("A product in your order is no longer on sale")));
            Product p = l.getProduct();
            BigDecimal qty = e.getValue();
            if (qty.compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) throw ApiException.badRequest(I18n.t("Quantities must be whole numbers between 1 and {0}", MAX_QUANTITY));
            BigDecimal sellable = sellable(p, s.getStockBuffer());
            if (sellable != null && qty.compareTo(sellable) > 0) {
                String title = l.getWebTitle() != null && !l.getWebTitle().isBlank() ? l.getWebTitle() : p.getName();
                throw ApiException.badRequest(sellable.signum() <= 0 ? I18n.t("{0} is sold out", title)
                        : I18n.t("Only {0} of {1} left", sellable.setScale(0, RoundingMode.DOWN).toPlainString(), title));
            }
            BigDecimal base = basePrice(l);
            BigDecimal unit = shown(p, base);
            OnlineOrderLine line = new OnlineOrderLine();
            line.setOnlineOrder(o);
            line.setProductId(p.getId());
            line.setName(l.getWebTitle() != null && !l.getWebTitle().isBlank() ? l.getWebTitle() : p.getName());
            line.setQuantity(qty);
            line.setBasePrice(base);
            line.setUnitPrice(unit);
            line.setLineTotal(unit.multiply(qty).setScale(2, RoundingMode.HALF_UP));
            subtotal = subtotal.add(line.getLineTotal());
            o.getLines().add(line);
        }
        if (s.getMinOrder() != null && subtotal.compareTo(s.getMinOrder()) < 0) {
            throw ApiException.badRequest(I18n.t("The minimum order is {0}", money(s.getMinOrder())));
        }
        BigDecimal fee = BigDecimal.ZERO;
        if (zone != null) {
            if (zone.getMinOrder() != null && subtotal.compareTo(zone.getMinOrder()) < 0) {
                throw ApiException.badRequest(I18n.t("The minimum order for {0} is {1}", zone.getName(), money(zone.getMinOrder())));
            }
            fee = zone.getFee() == null ? BigDecimal.ZERO : zone.getFee();
        }
        if (s.getFreeDeliveryOver() != null && subtotal.compareTo(s.getFreeDeliveryOver()) >= 0) fee = BigDecimal.ZERO;

        String code = newCode();
        while (orderRepository.findByCode(code).isPresent()) code = newCode();
        o.setCode(code);
        o.setDeliveryMethod(method);
        o.setCustomerName(name);
        o.setCustomerPhone(phone);
        o.setCustomerEmail(email);
        o.setCustomerAddress(address);
        if (zone != null) {
            o.setZoneId(zone.getId());
            o.setZoneName(zone.getName());
        }
        o.setDeliveryFee(fee);
        o.setNotes(clean(in.notes(), 300, I18n.t("The note"), false));
        o.setSubtotal(subtotal);
        o.setTotal(subtotal.add(fee));
        o.setLanguage("fr".equalsIgnoreCase(in.language()) ? "fr" : "en");
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus("PLACED");
        ev.setByUser("customer");
        o.getEvents().add(ev);
        OnlineOrder saved = orderRepository.save(o);
        auditLogService.record("ONLINE_ORDER_PLACED", "Online order " + saved.getCode() + " from " + name + " (" + money(saved.getTotal()) + ")", "OnlineOrder", saved.getId());
        OnlineMessages.send(mailService, storeSettingsService, saved,
                I18n.t("We received your order {0}", saved.getCode()),
                I18n.t("Thank you, {0}. We received your order {1} for {2}. We will confirm it shortly. You can follow it with this code.", name, saved.getCode(), money(saved.getTotal())));
        return track(saved);
    }

    // ------------------------------------------------------------------ following an order

    @Transactional(readOnly = true)
    public Track trackByCode(String code, String ip) {
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (s == null || !s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        throttle("t:" + ip, Math.max(60, s.getMaxPerIpPerHour() * 5));
        OnlineOrder o = orderRepository.findByCode(code == null ? "" : code.trim().toUpperCase()).orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        return track(o);
    }

    @Transactional
    public Track cancelByCustomer(String code, String ip) {
        openSettings();
        throttle("t:" + ip, 60);
        OnlineOrder o = orderRepository.findByCode(code == null ? "" : code.trim().toUpperCase()).orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        if (!o.getStatus().equals("PLACED")) throw ApiException.badRequest(I18n.t("This order is already being handled. Please call us to change it."));
        o.setStatus("CANCELLED");
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus("CANCELLED");
        ev.setNote(I18n.t("Cancelled by the customer"));
        ev.setByUser("customer");
        o.getEvents().add(ev);
        orderRepository.save(o);
        return track(o);
    }

    private static Track track(OnlineOrder o) {
        List<TrackLine> lines = o.getLines().stream().map(l -> new TrackLine(l.getName(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal())).toList();
        List<TrackStep> steps = o.getEvents().stream().map(e -> new TrackStep(e.getStatus(), e.getNote(), e.getCreatedAt())).toList();
        return new Track(o.getCode(), o.getStatus(), o.getDeliveryMethod(), o.getCustomerName(), o.getZoneName(), o.getSubtotal(), o.getDeliveryFee(),
                o.getTotal(), o.getRejectReason(), o.getCreatedAt(), lines, steps);
    }
}
