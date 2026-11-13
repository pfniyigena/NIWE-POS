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
    private final OnlinePaymentService payments;

    /** Requests per connection in the last hour, to slow down scripts that fill the order list. */
    private final OnlineCouponService coupons;
    private final OnlineTextService texts;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public OnlineStoreService(OnlineStoreSettingsRepository settingsRepository, OnlineListingRepository listingRepository,
                              OnlineDeliveryZoneRepository zoneRepository, OnlineOrderRepository orderRepository,
                              StockReservationService stockReservationService, StoreSettingsService storeSettingsService,
                              MailService mailService, AuditLogService auditLogService, OnlinePaymentService payments, OnlineCouponService coupons,
                              OnlineTextService texts) {
        this.payments = payments;
        this.texts = texts;
        this.coupons = coupons;
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

    /** The figures behind the stock the shop shows for a product (Shop settings > Products on the shop). */
    OnlineStoreDtos.StockInfo stockInfo(Product p, int buffer) {
        if (!p.isTrackStock() || p.isService()) return new OnlineStoreDtos.StockInfo(false, null, null, buffer, null);
        BigDecimal reserved = stockReservationService.reserved(p);
        BigDecimal available = stockReservationService.available(p);
        return new OnlineStoreDtos.StockInfo(true, available.add(reserved), reserved, buffer, available.subtract(BigDecimal.valueOf(buffer)));
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
        boolean open = !s.isPaused();
        return new PublicShop(name, s.getIntroText(), s.getContactPhone(), s.getPickupAddress(), open, open ? null : s.getPauseMessage(),
                s.isPickupEnabled(), s.isDeliveryEnabled(), s.isCodEnabled(), s.getMinOrder(), s.getFreeDeliveryOver(), zones,
                listingRepository.findShopCategories(), !payments.payMethods(s).isEmpty(), payments.payMethods(s), s.isAccountsEnabled(), currencyCode(), coupons.anyActive(), texts.channel(),
                s.isCartRemindersEnabled(), page(s));
    }

    /** The front page's wording as the public sees it. */
    static PublicPage page(OnlineStoreSettings s) {
        return new PublicPage(blank(s.getAnnouncementText()), blank(s.getAnnouncementLink()), blank(s.getHeroTitle()), blank(s.getHeroSubtitle()),
                blank(s.getHeroButtonText()), blank(s.getHeroButtonLink()), blank(s.getHeroImageUrl()), blank(s.getAboutText()), blank(s.getContactEmail()),
                blank(s.getOpeningHours()), links(s.getFooterLinks(), 8), links(s.getSocialLinks(), 6));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** "Label|address" lines. A line that is not valid is skipped (staff's input is checked when saved, this is only a second guard). */
    static List<PublicLink> links(String text, int max) {
        List<PublicLink> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\r?\\n")) {
            int bar = line.indexOf('|');
            if (bar < 1) continue;
            String label = line.substring(0, bar).trim();
            String url = line.substring(bar + 1).trim();
            if (label.isEmpty() || !safeLink(url)) continue;
            out.add(new PublicLink(label.length() > 40 ? label.substring(0, 40) : label, url));
            if (out.size() >= max) break;
        }
        return out;
    }

    /** An address on this site (/shop...), or https / http, or mailto and tel. Never javascript: or anything else. */
    static boolean safeLink(String url) {
        if (url == null || url.isBlank() || url.length() > 300 || url.chars().anyMatch(c -> c < 32 || c == ' ' || c == '"' || c == '<' || c == '>')) return false;
        String u = url.toLowerCase(java.util.Locale.ROOT);
        return (u.startsWith("/") && !u.startsWith("//")) || u.startsWith("https://") || u.startsWith("http://") || u.startsWith("mailto:") || u.startsWith("tel:");
    }

    static final int DEFAULT_PAGE_SIZE = 16;
    static final int MAX_PAGE_SIZE = 60;

    /** One page of the catalogue, searched and filtered in the database so a shop with many products never sends them all at once. */
    @Transactional(readOnly = true)
    public PublicProductPage products(int page, int size, String q, String category) {
        OnlineStoreSettings s = openSettings();
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageNo = Math.max(0, page);
        String needle = q == null ? "" : q.trim().toLowerCase().replace("\\", "").replace("%", "").replace("_", "");
        if (needle.length() > 60) needle = needle.substring(0, 60);
        String cat = category == null ? "" : category.trim();
        org.springframework.data.domain.Page<OnlineListing> result =
                listingRepository.findShopPage(needle, cat, org.springframework.data.domain.PageRequest.of(pageNo, pageSize));
        List<PublicProduct> items = new ArrayList<>();
        for (OnlineListing l : result.getContent()) items.add(publicProduct(l, s));
        return new PublicProductPage(items, pageNo, pageSize, result.getTotalElements(), result.getTotalPages());
    }

    /** The currency the shop charges in (the business currency), shown next to every price on the public shop. */
    String currencyCode() {
        String c = storeSettingsService.getSettingsEntity().getCurrencyCode();
        return c == null || c.isBlank() ? "RWF" : c.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** One product as the public shop shows it: web title and description, today's price, a picture link, and a stock word (never a number). */
    PublicProduct publicProduct(OnlineListing l, OnlineStoreSettings s) {
        Product p = l.getProduct();
        String title = l.getWebTitle() != null && !l.getWebTitle().isBlank() ? l.getWebTitle() : p.getName();
        String description = l.getWebDescription() != null && !l.getWebDescription().isBlank() ? l.getWebDescription() : p.getDescription();
        return new PublicProduct(p.getId(), title, description, shown(p, basePrice(l)),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getIconDataUrl() == null || p.getIconDataUrl().isBlank() ? null : "/api/public/store/products/" + p.getId() + "/image",
                stockLabel(sellable(p, s.getStockBuffer())), wasPrice(l, p), 0, 0);
    }

    /** The normal price, shown struck through, when staff set a lower online price. */
    private static BigDecimal wasPrice(OnlineListing l, Product p) {
        if (l.getOnlinePrice() == null || l.getOnlinePrice().compareTo(p.getUnitPrice()) >= 0) return null;
        return shown(p, p.getUnitPrice());
    }

    /** Scaled copies already made, so a busy shop does not decode and shrink the same picture again for every visitor. */
    private static final Map<String, Image> SCALED = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > 400;
        }
    });

    /**
     * The product's own picture as bytes. With a width (in pixels, 64 to 1200) the picture is shrunk to at most that width, so the product
     * grid does not download full-size photos; without one, or when the picture is already small enough, it is returned as it is.
     */
    @Transactional(readOnly = true)
    public Image image(Long productId, int width) {
        OnlineListing l = listingRepository.findByProductId(productId).filter(OnlineListing::isListed).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        String url = l.getProduct().getIconDataUrl();
        if (url == null || !url.startsWith("data:") || !url.contains(";base64,")) throw ApiException.notFound(I18n.t("Not found."));
        String type = url.substring(5, url.indexOf(';'));
        if (!type.startsWith("image/")) throw ApiException.notFound(I18n.t("Not found."));
        int w = width <= 0 ? 0 : Math.max(64, Math.min(width, 1200));
        String key = productId + ":" + w + ":" + url.length() + ":" + url.hashCode();
        if (w > 0) {
            Image cached = SCALED.get(key);
            if (cached != null) return cached;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(url.substring(url.indexOf(";base64,") + 8));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound(I18n.t("Not found."));
        }
        Image out = w == 0 ? new Image(type, bytes) : scale(type, bytes, w);
        if (w > 0) SCALED.put(key, out);
        return out;
    }

    /** Shrinks a picture to at most maxWidth pixels wide (JPEG, or PNG when it has transparency). Anything that cannot be read or is small enough stays as it is. */
    static Image scale(String type, byte[] bytes, int maxWidth) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (src == null || src.getWidth() <= maxWidth) return new Image(type, bytes);
            boolean alpha = src.getColorModel().hasAlpha();
            int w = src.getWidth();
            int h = src.getHeight();
            java.awt.image.BufferedImage cur = src;
            // Halve while far above the target, then the last step: a straight big jump looks jagged.
            while (w / 2 >= maxWidth) {
                w = w / 2;
                h = Math.max(1, h / 2);
                cur = resize(cur, w, h, alpha);
            }
            int th = Math.max(1, (int) Math.round(h * (maxWidth / (double) w)));
            cur = resize(cur, maxWidth, th, alpha);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(cur, alpha ? "png" : "jpg", bos)) return new Image(type, bytes);
            byte[] scaled = bos.toByteArray();
            return scaled.length >= bytes.length ? new Image(type, bytes) : new Image(alpha ? "image/png" : "image/jpeg", scaled);
        } catch (java.io.IOException | RuntimeException e) {
            return new Image(type, bytes);
        }
    }

    private static java.awt.image.BufferedImage resize(java.awt.image.BufferedImage src, int w, int h, boolean alpha) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, alpha ? java.awt.image.BufferedImage.TYPE_INT_ARGB : java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            if (!alpha) {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, w, h);
            }
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    public record Image(String contentType, byte[] bytes) {
    }

    // ------------------------------------------------------------------ placing an order

    @Transactional
    public Track place(PlaceRequest in, String ip) {
        return place(in, ip, null);
    }

    /** shopCustomerId: the signed-in customer, or null for a guest. */
    @Transactional
    public Track place(PlaceRequest in, String ip, Long shopCustomerId) {
        OnlineStoreSettings s = openSettings();
        if (in.website() != null && !in.website().isBlank()) throw ApiException.badRequest(I18n.t("The order could not be placed"));
        if (s.isPaused()) {
            throw ApiException.badRequest(I18n.t("We are not taking orders right now.") + (s.getPauseMessage() == null ? "" : " " + s.getPauseMessage()));
        }
        throttle(ip, s.getMaxPerIpPerHour());
        // How the customer pays: in cash on delivery or at pick-up, or online (mobile money, card) before the shop sees the order.
        String pay = in.payment() == null || in.payment().isBlank() ? "CASH" : in.payment().trim().toUpperCase();
        boolean online = !pay.equals("CASH");
        if (online) {
            if (!payments.payMethods(s).contains(pay)) throw ApiException.badRequest(I18n.t("Paying online is not available right now. Please choose to pay in cash."));
        } else if (!s.isCodEnabled()) {
            throw ApiException.badRequest(I18n.t("Paying in cash is not available right now. Please pay online."));
        }
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
        if (orderRepository.countByCustomerPhoneAndStatus(phone, "PLACED") + orderRepository.countByCustomerPhoneAndStatus(phone, "AWAITING_PAYMENT") >= s.getMaxPendingPerPhone()) {
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

        // A coupon: the server decides what it is worth (under a lock on the coupon); whatever the browser showed is not used.
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal waived = BigDecimal.ZERO;
        String couponCode = OnlineCouponService.normalize(in.couponCode());
        if (!couponCode.isEmpty()) {
            OnlineCouponService.Applied a = coupons.evaluate(couponCode, subtotal, fee, method, phone, shopCustomerId, true);
            discount = a.discount();
            waived = a.feeWaived();
            fee = fee.subtract(waived);
            List<BigDecimal> parts = OnlineCouponService.allocate(o.getLines().stream().map(OnlineOrderLine::getLineTotal).toList(), discount);
            for (int i = 0; i < parts.size(); i++) o.getLines().get(i).setDiscountAmount(parts.get(i));
            o.setCouponId(a.coupon().getId());
            o.setCouponCode(a.coupon().getCode());
            o.setCouponKey(OnlineCouponService.phoneKey(phone));
        }
        o.setDiscountAmount(discount);
        o.setFeeWaived(waived);
        if (online && subtotal.subtract(discount).add(fee).signum() <= 0) {
            throw ApiException.badRequest(I18n.t("Your order is free, so there is nothing to pay online. Please choose to pay in cash."));
        }

        String code = newCode();
        while (orderRepository.findByCode(code).isPresent()) code = newCode();
        o.setCode(code);
        o.setShopCustomerId(shopCustomerId);
        o.setDeliveryMethod(method);
        o.setCustomerName(name);
        o.setCustomerPhone(phone);
        o.setPhoneKey(OnlineCouponService.phoneKey(phone));
        // The tick box counts only while texting is on: a customer is never texted without having ticked it, and never for a channel that is off.
        o.setTextOptIn(Boolean.TRUE.equals(in.textUpdates()) && texts.enabled());
        o.setCustomerEmail(email);
        o.setCustomerAddress(address);
        if (zone != null) {
            o.setZoneId(zone.getId());
            o.setZoneName(zone.getName());
        }
        o.setDeliveryFee(fee);
        o.setNotes(clean(in.notes(), 300, I18n.t("The note"), false));
        o.setSubtotal(subtotal);
        o.setTotal(subtotal.subtract(discount).add(fee));
        o.setLanguage("fr".equalsIgnoreCase(in.language()) ? "fr" : "en");
        // An order paid online is not the shop's to answer until the payment is confirmed; it waits as AWAITING_PAYMENT (holding no stock).
        o.setStatus(online ? "AWAITING_PAYMENT" : "PLACED");
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(o.getStatus());
        ev.setByUser("customer");
        o.getEvents().add(ev);
        OnlineOrder saved = orderRepository.save(o);
        auditLogService.record("ONLINE_ORDER_PLACED", "Online order " + saved.getCode() + " from " + name + " (" + money(saved.getTotal()) + (online ? ", to be paid online" : "") + ")", "OnlineOrder", saved.getId());
        if (online) {
            // Starting the payment can fail (bad number, provider down): the whole order is then not placed, and the customer fixes it and sends again.
            payments.start(saved, pay, in.payPhone(), s);
            orderRepository.save(saved);
        } else {
            OnlineMessages.send(mailService, storeSettingsService, saved,
                    OnlineMessages.m("We received your order {0}", saved.getCode()),
                    OnlineMessages.m("Thank you, {0}. We received your order {1} for {2}. We will confirm it shortly. You can follow it with this code.", name, saved.getCode(), money(saved.getTotal())));
            OnlineMessages.notifyShop(mailService, storeSettingsService, s, saved);
        }
        return track(saved);
    }

    /**
     * "Apply" on the order form: what this code would be worth on this cart, using the shop's current prices. It changes nothing; placing the order
     * checks everything again. Limited per connection so codes cannot be tried one after another.
     */
    @Transactional(readOnly = true)
    public CouponResult checkCoupon(CouponCheck in, String ip, Long shopCustomerId) {
        OnlineStoreSettings s = openSettings();
        coupons.throttle(ip, 60);
        String method = in.method() == null ? "" : in.method().trim().toUpperCase();
        List<PublicLine> lines = in.lines() == null ? List.of() : in.lines();
        if (lines.isEmpty() || lines.size() > MAX_LINES) throw ApiException.badRequest(I18n.t("Add something to your order first"));
        BigDecimal subtotal = BigDecimal.ZERO;
        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        for (PublicLine l : lines) {
            if (l.quantity() == null || l.quantity().signum() <= 0 || l.quantity().compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) continue;
            wanted.merge(l.productId(), l.quantity(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : wanted.entrySet()) {
            OnlineListing l = listingRepository.findByProductId(e.getKey()).filter(OnlineListing::isListed).filter(x -> x.getProduct().isActive()).orElse(null);
            if (l == null) continue;
            subtotal = subtotal.add(shown(l.getProduct(), basePrice(l)).multiply(e.getValue()).setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal fee = BigDecimal.ZERO;
        if (method.equals("DELIVERY")) {
            OnlineDeliveryZone zone = in.zoneId() == null ? null : zoneRepository.findById(in.zoneId()).filter(OnlineDeliveryZone::isActive).orElse(null);
            if (zone != null && zone.getFee() != null) fee = zone.getFee();
            if (s.getFreeDeliveryOver() != null && subtotal.compareTo(s.getFreeDeliveryOver()) >= 0) fee = BigDecimal.ZERO;
        }
        OnlineCouponService.Applied a = coupons.evaluate(in.code(), subtotal, fee, method, in.phone(), shopCustomerId, false);
        return new CouponResult(a.coupon().getCode(), a.coupon().getType(), a.discount(), a.feeWaived());
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
        if (!o.getStatus().equals("PLACED") && !o.getStatus().equals("AWAITING_PAYMENT")) throw ApiException.badRequest(I18n.t("This order is already being handled. Please call us to change it."));
        o.setStatus("CANCELLED");
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus("CANCELLED");
        ev.setNote(I18n.t("Cancelled by the customer"));
        ev.setByUser("customer");
        o.getEvents().add(ev);
        boolean refundDue = payments.afterClosed(o);
        orderRepository.save(o);
        OnlineMessages.send(mailService, storeSettingsService, o, OnlineMessages.m("Your order {0} is cancelled", o.getCode()),
                refundDue ? OnlineMessages.m("Your order {0} has been cancelled. We will refund {1} to the account you paid with.", o.getCode(), money(o.getPaidAmount()))
                        : OnlineMessages.m("Your order {0} has been cancelled.", o.getCode()));
        return track(o);
    }

    /** The customer tries to pay again (or with the other way) for an order that is waiting for its payment. */
    @Transactional
    public Track payAgain(String code, PayRequest in, String ip) {
        OnlineStoreSettings s = openSettings();
        throttle("t:" + ip, 60);
        OnlineOrder o = orderRepository.findByCode(code == null ? "" : code.trim().toUpperCase()).orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        if (!o.getStatus().equals("AWAITING_PAYMENT") || o.getPaymentStatus().equals("PAID")) throw ApiException.badRequest(I18n.t("This order cannot be paid now."));
        payments.start(o, in == null ? null : in.payment(), in == null ? null : in.payPhone(), s);
        orderRepository.save(o);
        return track(o);
    }

    private Track track(OnlineOrder o) {
        List<TrackLine> lines = o.getLines().stream().map(l -> new TrackLine(l.getName(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal())).toList();
        List<TrackStep> steps = o.getEvents().stream().map(e -> new TrackStep(e.getStatus(), e.getNote(), e.getCreatedAt())).toList();
        return new Track(o.getCode(), o.getStatus(), o.getDeliveryMethod(), o.getCustomerName(), o.getZoneName(), o.getSubtotal(), o.getDeliveryFee(),
                o.getTotal(), o.getRejectReason(), o.getCreatedAt(), lines, steps, payments.info(o), o.getCouponCode(), o.getDiscountAmount(), o.getFeeWaived());
    }
}
