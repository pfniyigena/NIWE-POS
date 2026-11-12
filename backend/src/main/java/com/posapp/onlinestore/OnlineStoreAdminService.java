package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Online Store settings, the products on the shop and the delivery areas. */
@Service
public class OnlineStoreAdminService {

    private final OnlineStoreService storeService;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final OnlineListingRepository listingRepository;
    private final OnlineDeliveryZoneRepository zoneRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final OnlinePaymentService payments;
    private final OnlineCouponRepository couponRepository;
    private final OnlineTextService texts;
    private final OnlineOrderRepository orderRepository;

    public OnlineStoreAdminService(OnlineStoreService storeService, OnlineStoreSettingsRepository settingsRepository,
                                   OnlineListingRepository listingRepository, OnlineDeliveryZoneRepository zoneRepository,
                                   CatalogService catalogService, AuditLogService auditLogService, OnlinePaymentService payments,
                                   OnlineCouponRepository couponRepository, OnlineOrderRepository orderRepository, OnlineTextService texts) {
        this.payments = payments;
        this.texts = texts;
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
        this.storeService = storeService;
        this.settingsRepository = settingsRepository;
        this.listingRepository = listingRepository;
        this.zoneRepository = zoneRepository;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
    }

    private static String text(String v, int max) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) return null;
        if (t.length() > max) throw ApiException.badRequest(I18n.t("That text is too long"));
        return t;
    }

    private static BigDecimal money(BigDecimal v, String what) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw ApiException.badRequest(what + I18n.t(" cannot be negative"));
        return v;
    }

    // ------------------------------------------------------------------ text messages

    /** Checks and applies the text message part of the settings. Switching texting on needs a provider that is available and carries the channel. */
    private void applyTexting(OnlineStoreSettings s, SettingsDto in) {
        String channel = in.textChannel() == null || in.textChannel().isBlank() ? "SMS" : in.textChannel().trim().toUpperCase();
        if (!channel.equals("SMS") && !channel.equals("WHATSAPP")) throw ApiException.badRequest(I18n.t("Choose SMS or WhatsApp for text messages"));
        String cc = in.textCountryCode() == null ? "" : in.textCountryCode().trim();
        if (!cc.matches("[0-9]{1,4}")) throw ApiException.badRequest(I18n.t("The country code must be 1 to 4 digits, for example 250"));
        if (in.textDailyLimit() < 0 || in.textDailyLimit() > 100000) throw ApiException.badRequest(I18n.t("The daily limit of text messages must be between 0 and 100000"));
        String provider = text(in.textProvider(), 30);
        if (provider != null) {
            var chosen = texts.find(provider);
            if (chosen == null || !chosen.available()) throw ApiException.badRequest(I18n.t("Choose a text provider that is available"));
            if (!chosen.supports(channel)) throw ApiException.badRequest(I18n.t("This text provider cannot send on the channel you chose"));
            provider = chosen.code();
        }
        String phone = text(in.notifyPhone(), 40);
        if (phone != null && OnlineTextService.toE164(phone, cc) == null) throw ApiException.badRequest(I18n.t("The shop phone number for texts does not look right"));
        s.setTextProvider(provider);
        s.setTextChannel(channel);
        s.setTextCountryCode(cc);
        s.setTextDailyLimit(in.textDailyLimit());
        s.setNotifyPhone(phone);
    }

    public List<TextProviderDto> textProviders() {
        return texts.providers().stream().map(p -> new TextProviderDto(p.code(), p.label(), p.available(),
                java.util.stream.Stream.of("SMS", "WHATSAPP").filter(p::supports).toList())).toList();
    }

    @Transactional(readOnly = true)
    public TextPage textLog(int page, int size) {
        var p = texts.history(page, size);
        return new TextPage(p.getContent().stream().map(m -> new TextMessageDto(m.getId(), m.getCreatedAt(), m.getChannel(), m.getToPhone(), m.getPurpose(),
                m.getBody(), m.getStatus(), m.getProvider(), m.getError(), m.getOnlineOrderId())).toList(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** Sends one text to the number typed, through the same limits and log as every other text. */
    public TextTestResult testText(TextTestRequest in) {
        if (in == null || in.phone() == null || in.phone().isBlank()) throw ApiException.badRequest(I18n.t("Enter the phone number to send the test to"));
        if (!texts.enabled()) throw ApiException.badRequest(I18n.t("Text messages are off. Choose a provider in the shop settings first."));
        var o = texts.test(in.phone(), in.text());
        auditLogService.record("ONLINE_TEXT_TEST", "Sent a test text message (" + o.status() + ")");
        return new TextTestResult(o.status(), o.error(), texts.toE164(in.phone()));
    }

    // ------------------------------------------------------------------ settings

    @Transactional
    public SettingsDto settings() {
        return toDto(storeService.settings());
    }

    @Transactional
    public SettingsDto update(SettingsDto in) {
        OnlineStoreSettings s = storeService.settings();
        s.setEnabled(in.enabled());
        s.setPaused(in.paused());
        s.setPauseMessage(text(in.pauseMessage(), 200));
        s.setShopName(text(in.shopName(), 120));
        s.setIntroText(text(in.introText(), 300));
        s.setContactPhone(text(in.contactPhone(), 40));
        String notify = text(in.notifyEmail(), 160);
        if (notify != null && !notify.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest(I18n.t("The shop e-mail address does not look right"));
        s.setNotifyEmail(notify);
        if (in.unansweredAlertMinutes() < 0 || in.unansweredAlertMinutes() > 1440) throw ApiException.badRequest(I18n.t("The alert time must be between 0 and 1440 minutes"));
        s.setUnansweredAlertMinutes(in.unansweredAlertMinutes());
        s.setPickupAddress(text(in.pickupAddress(), 240));
        s.setPickupEnabled(in.pickupEnabled());
        s.setDeliveryEnabled(in.deliveryEnabled());
        s.setCodEnabled(in.codEnabled());
        if (in.stockBuffer() < 0 || in.stockBuffer() > 1000) throw ApiException.badRequest(I18n.t("The stock buffer must be between 0 and 1000"));
        s.setStockBuffer(in.stockBuffer());
        s.setMinOrder(money(in.minOrder(), I18n.t("The minimum order")));
        s.setFreeDeliveryOver(in.freeDeliveryOver() == null || in.freeDeliveryOver().signum() <= 0 ? null : in.freeDeliveryOver());
        if (in.maxPendingPerPhone() < 1 || in.maxPendingPerPhone() > 50) throw ApiException.badRequest(I18n.t("Orders waiting per phone must be between 1 and 50"));
        s.setMaxPendingPerPhone(in.maxPendingPerPhone());
        if (in.maxPerIpPerHour() < 1 || in.maxPerIpPerHour() > 1000) throw ApiException.badRequest(I18n.t("Orders per connection per hour must be between 1 and 1000"));
        s.setMaxPerIpPerHour(in.maxPerIpPerHour());
        if (in.deliveryProductId() == null) {
            s.setDeliveryProductId(null);
        } else {
            Product p = catalogService.getProductOrThrow(in.deliveryProductId());
            if (!p.isService()) throw ApiException.badRequest(I18n.t("The delivery product must be a service product, not stock"));
            s.setDeliveryProductId(p.getId());
        }
        s.setAccountsEnabled(in.accountsEnabled());
        s.setOnlinePaymentsEnabled(in.onlinePaymentsEnabled());
        String prov = text(in.paymentProvider(), 30);
        if (in.onlinePaymentsEnabled()) {
            var chosen = payments.provider(prov);
            if (chosen == null || !chosen.available()) throw ApiException.badRequest(I18n.t("Choose a payment provider that is available before switching online payments on"));
            prov = chosen.code();
        }
        s.setPaymentProvider(prov == null ? null : prov.toUpperCase());
        if (in.paymentExpiryMinutes() < 5 || in.paymentExpiryMinutes() > 1440) throw ApiException.badRequest(I18n.t("The time to pay must be between 5 and 1440 minutes"));
        s.setPaymentExpiryMinutes(in.paymentExpiryMinutes());
        applyTexting(s, in);
        if (in.returnWindowDays() < 0 || in.returnWindowDays() > 365) throw ApiException.badRequest(I18n.t("The return period must be between 0 and 365 days"));
        s.setReturnWindowDays(in.returnWindowDays());
        settingsRepository.save(s);
        auditLogService.record("ONLINE_STORE_SETTINGS", "Updated the Online Store settings (" + (s.isEnabled() ? "on" : "off") + (s.isPaused() ? ", paused" : "") + ")");
        return toDto(s);
    }

    private SettingsDto toDto(OnlineStoreSettings s) {
        String productName = null;
        if (s.getDeliveryProductId() != null) {
            try {
                productName = catalogService.getProductOrThrow(s.getDeliveryProductId()).getName();
            } catch (ApiException e) {
                productName = null;
            }
        }
        return new SettingsDto(s.isEnabled(), s.isPaused(), s.getPauseMessage(), s.getShopName(), s.getIntroText(), s.getContactPhone(), s.getPickupAddress(),
                s.isPickupEnabled(), s.isDeliveryEnabled(), s.isCodEnabled(), s.getStockBuffer(), s.getMinOrder(), s.getFreeDeliveryOver(),
                s.getMaxPendingPerPhone(), s.getMaxPerIpPerHour(), s.getDeliveryProductId(), productName, s.getNotifyEmail(), s.getUnansweredAlertMinutes(),
                s.isOnlinePaymentsEnabled(), s.getPaymentProvider(), s.getPaymentExpiryMinutes(), s.isAccountsEnabled(),
                s.getTextProvider(), s.getTextChannel(), s.getTextCountryCode(), s.getTextDailyLimit(), s.getNotifyPhone(), s.getReturnWindowDays());
    }

    // ------------------------------------------------------------------ listings

    @Transactional
    public List<ListingDto> listings() {
        int buffer = storeService.settings().getStockBuffer();
        return listingRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(l -> toDto(l, buffer)).toList();
    }

    @Transactional
    public ListingDto saveListing(ListingRequest in) {
        Product p = catalogService.getProductOrThrow(in.productId());
        OnlineListing l = listingRepository.findByProductId(p.getId()).orElseGet(() -> {
            OnlineListing created = new OnlineListing();
            created.setProduct(p);
            return created;
        });
        if (in.listed() != null) l.setListed(in.listed());
        l.setWebTitle(text(in.webTitle(), 160));
        l.setWebDescription(text(in.webDescription(), 2000));
        if (in.onlinePrice() != null && in.onlinePrice().signum() < 0) throw ApiException.badRequest(I18n.t("The online price cannot be negative"));
        l.setOnlinePrice(in.onlinePrice());
        if (in.sortOrder() != null) l.setSortOrder(in.sortOrder());
        if (in.featured() != null) l.setFeatured(in.featured());
        if (in.bestSeller() != null) l.setBestSeller(in.bestSeller());
        if (in.specialOffer() != null) l.setSpecialOffer(in.specialOffer());
        if (in.newArrival() != null) l.setNewArrival(in.newArrival());
        OnlineListing saved = listingRepository.save(l);
        auditLogService.record("ONLINE_LISTING_SAVED", (saved.isListed() ? "Listed " : "Unlisted ") + p.getName() + " on the online shop", "OnlineListing", saved.getId());
        return toDto(saved, storeService.settings().getStockBuffer());
    }

    @Transactional
    public void removeListing(Long id) {
        OnlineListing l = listingRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Listing not found")));
        listingRepository.delete(l);
        auditLogService.record("ONLINE_LISTING_REMOVED", "Removed " + l.getProduct().getName() + " from the online shop", "OnlineListing", id);
    }

    private ListingDto toDto(OnlineListing l, int buffer) {
        Product p = l.getProduct();
        return new ListingDto(l.getId(), p.getId(), p.getName(), p.getSku(), p.getCategory() == null ? null : p.getCategory().getName(), p.getUnitPrice(),
                l.isListed(), l.getWebTitle(), l.getWebDescription(), l.getOnlinePrice(), l.getSortOrder(),
                p.getIconDataUrl() != null && !p.getIconDataUrl().isBlank(), OnlineStoreService.stockLabel(storeService.sellable(p, buffer)),
                l.isFeatured(), l.isBestSeller(), l.isSpecialOffer(), l.isNewArrival(), storeService.stockInfo(p, buffer));
    }


    // ------------------------------------------------------------------ front page wording (v1.35.0)

    @Transactional
    public PageContent pageContent() {
        OnlineStoreSettings s = storeService.settings();
        return new PageContent(s.getAnnouncementText(), s.getAnnouncementLink(), s.getHeroTitle(), s.getHeroSubtitle(), s.getHeroButtonText(), s.getHeroButtonLink(),
                s.getHeroImageUrl(), s.getAboutText(), s.getContactEmail(), s.getOpeningHours(), s.getFooterLinks(), s.getSocialLinks());
    }

    @Transactional
    public PageContent savePageContent(PageContent in) {
        OnlineStoreSettings s = storeService.settings();
        s.setAnnouncementText(text(in.announcementText(), 200));
        s.setAnnouncementLink(link(in.announcementLink(), I18n.t("The announcement link")));
        s.setHeroTitle(text(in.heroTitle(), 120));
        s.setHeroSubtitle(text(in.heroSubtitle(), 300));
        s.setHeroButtonText(text(in.heroButtonText(), 40));
        s.setHeroButtonLink(link(in.heroButtonLink(), I18n.t("The banner button link")));
        String image = text(in.heroImageUrl(), 300);
        if (image != null && !(image.startsWith("https://") || (image.startsWith("/") && !image.startsWith("//"))) || image != null && !OnlineStoreService.safeLink(image)) {
            throw ApiException.badRequest(I18n.t("The banner picture must be an https address."));
        }
        s.setHeroImageUrl(image);
        s.setAboutText(text(in.aboutText(), 400));
        String mail = text(in.contactEmail(), 160);
        if (mail != null && !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest(I18n.t("The contact e-mail address does not look right"));
        s.setContactEmail(mail);
        s.setOpeningHours(text(in.openingHours(), 200));
        s.setFooterLinks(lines(in.footerLinks(), 8, 1200, I18n.t("Footer links")));
        s.setSocialLinks(lines(in.socialLinks(), 6, 800, I18n.t("Social links")));
        settingsRepository.save(s);
        auditLogService.record("ONLINE_STORE_FRONT_PAGE", "Updated the wording of the shop's front page");
        return pageContent();
    }

    private static String link(String v, String what) {
        String x = text(v, 300);
        if (x != null && !OnlineStoreService.safeLink(x)) throw ApiException.badRequest(I18n.t("{0} must start with / or https://", what));
        return x;
    }

    /** Lines written "Label|address": checked one by one, blank lines dropped. */
    private static String lines(String v, int max, int maxChars, String what) {
        if (v == null || v.isBlank()) return null;
        List<String> out = new java.util.ArrayList<>();
        for (String line : v.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int bar = t.indexOf('|');
            if (bar < 1 || bar > 40 || !OnlineStoreService.safeLink(t.substring(bar + 1).trim())) {
                throw ApiException.badRequest(I18n.t("{0}: write each line as Label|address, the address starting with / or https://. Problem with: {1}", what, t.length() > 40 ? t.substring(0, 40) : t));
            }
            out.add(t.substring(0, bar).trim() + "|" + t.substring(bar + 1).trim());
        }
        if (out.size() > max) throw ApiException.badRequest(I18n.t("{0}: at most {1} lines", what, max));
        String joined = String.join("\n", out);
        if (joined.length() > maxChars) throw ApiException.badRequest(I18n.t("{0} is too long", what));
        return joined.isEmpty() ? null : joined;
    }

    // ------------------------------------------------------------------ delivery areas

    @Transactional(readOnly = true)
    public List<ZoneDto> zones() {
        return zoneRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(OnlineStoreAdminService::toDto).toList();
    }

    @Transactional
    public ZoneDto createZone(ZoneRequest in) {
        return save(new OnlineDeliveryZone(), in);
    }

    @Transactional
    public ZoneDto updateZone(Long id, ZoneRequest in) {
        return save(zoneRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Delivery area not found"))), in);
    }

    @Transactional
    public void deleteZone(Long id) {
        // Orders keep the area's name, so removing it never changes an order that was already placed.
        zoneRepository.delete(zoneRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Delivery area not found"))));
    }

    private ZoneDto save(OnlineDeliveryZone z, ZoneRequest in) {
        z.setName(in.name().trim());
        z.setFee(money(in.fee(), I18n.t("The fee")));
        z.setMinOrder(money(in.minOrder(), I18n.t("The minimum order")));
        if (in.active() != null) z.setActive(in.active());
        if (in.sortOrder() != null) z.setSortOrder(in.sortOrder());
        return toDto(zoneRepository.save(z));
    }

    private static ZoneDto toDto(OnlineDeliveryZone z) {
        return new ZoneDto(z.getId(), z.getName(), z.getFee(), z.getMinOrder(), z.isActive(), z.getSortOrder());
    }

    // ------------------------------------------------------------------ coupons

    @Transactional(readOnly = true)
    public List<CouponDto> coupons() {
        return couponRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public CouponDto createCoupon(CouponRequest in) {
        return saveCoupon(new OnlineCoupon(), in);
    }

    @Transactional
    public CouponDto updateCoupon(Long id, CouponRequest in) {
        return saveCoupon(couponRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Coupon not found"))), in);
    }

    /** Orders keep the code they used, so deleting a coupon never changes an order that was already placed. */
    @Transactional
    public void deleteCoupon(Long id) {
        OnlineCoupon c = couponRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Coupon not found")));
        couponRepository.delete(c);
        auditLogService.record("ONLINE_COUPON_DELETED", "Deleted coupon " + c.getCode(), "OnlineCoupon", id);
    }

    private CouponDto saveCoupon(OnlineCoupon c, CouponRequest in) {
        String code = OnlineCouponService.normalize(in.code());
        if (!code.matches("[A-Z0-9_-]{3,40}")) throw ApiException.badRequest(I18n.t("The coupon code must be 3 to 40 letters, digits, - or _ (no spaces)."));
        couponRepository.findByCode(code).filter(x -> !x.getId().equals(c.getId())).ifPresent(x -> {
            throw ApiException.conflict(I18n.t("There is already a coupon with this code."));
        });
        String type = in.type() == null ? "" : in.type().trim().toUpperCase(java.util.Locale.ROOT);
        BigDecimal value = in.value() == null ? BigDecimal.ZERO : in.value();
        BigDecimal max = in.maxDiscount() == null || in.maxDiscount().signum() <= 0 ? null : in.maxDiscount();
        switch (type) {
            case "PERCENT" -> {
                if (value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest(I18n.t("A percentage must be above 0 and at most 100."));
            }
            case "FIXED" -> {
                if (value.signum() <= 0) throw ApiException.badRequest(I18n.t("The amount off must be above 0."));
                max = null;
            }
            case "FREE_DELIVERY" -> {
                value = BigDecimal.ZERO;
                max = null;
            }
            default -> throw ApiException.badRequest(I18n.t("Choose what the coupon gives."));
        }
        BigDecimal min = in.minOrder() == null ? BigDecimal.ZERO : in.minOrder();
        if (min.signum() < 0) throw ApiException.badRequest(I18n.t("The minimum order cannot be negative."));
        if (in.validFrom() != null && in.validUntil() != null && in.validUntil().isBefore(in.validFrom())) throw ApiException.badRequest(I18n.t("The coupon cannot end before it starts."));
        if (in.maxUses() != null && in.maxUses() < 1) throw ApiException.badRequest(I18n.t("The number of uses must be at least 1."));
        boolean isNew = c.getId() == null;
        c.setCode(code);
        c.setDescription(text(in.description(), 160));
        c.setType(type);
        c.setValue(value.setScale(2, java.math.RoundingMode.HALF_UP));
        c.setMaxDiscount(max);
        c.setMinOrder(min);
        c.setValidFrom(in.validFrom());
        c.setValidUntil(in.validUntil());
        c.setMaxUses(in.maxUses());
        if (in.oncePerCustomer() != null) c.setOncePerCustomer(in.oncePerCustomer());
        if (in.active() != null) c.setActive(in.active());
        OnlineCoupon saved = couponRepository.save(c);
        auditLogService.record(isNew ? "ONLINE_COUPON_CREATED" : "ONLINE_COUPON_UPDATED", (isNew ? "Created" : "Changed") + " coupon " + code, "OnlineCoupon", saved.getId());
        return toDto(saved);
    }

    private CouponDto toDto(OnlineCoupon c) {
        return new CouponDto(c.getId(), c.getCode(), c.getDescription(), c.getType(), c.getValue(), c.getMaxDiscount(), c.getMinOrder(), c.getValidFrom(), c.getValidUntil(),
                c.getMaxUses(), c.isOncePerCustomer(), c.isActive(), orderRepository.countByCouponIdAndStatusNotIn(c.getId(), OnlineCouponService.RELEASED));
    }
}
