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
    private final OnlineOrderRepository orderRepository;

    public OnlineStoreAdminService(OnlineStoreService storeService, OnlineStoreSettingsRepository settingsRepository,
                                   OnlineListingRepository listingRepository, OnlineDeliveryZoneRepository zoneRepository,
                                   CatalogService catalogService, AuditLogService auditLogService, OnlinePaymentService payments,
                                   OnlineCouponRepository couponRepository, OnlineOrderRepository orderRepository) {
        this.payments = payments;
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
                s.isOnlinePaymentsEnabled(), s.getPaymentProvider(), s.getPaymentExpiryMinutes(), s.isAccountsEnabled());
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
                p.getIconDataUrl() != null && !p.getIconDataUrl().isBlank(), OnlineStoreService.stockLabel(storeService.sellable(p, buffer)));
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
