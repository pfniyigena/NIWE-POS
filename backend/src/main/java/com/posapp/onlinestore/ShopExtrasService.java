package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.invoice.InvoiceService;
import com.posapp.onlinestore.OnlineStoreDtos.AddressDto;
import com.posapp.onlinestore.OnlineStoreDtos.AddressRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a signed-in shop customer keeps on top of the account (v1.47.0): saved delivery addresses and a wishlist (the heart on a product); and the invoice
 * of a completed order as a PDF, for the customer who can prove the order is theirs (the phone number of the order, or the signed-in account that holds it).
 */
@Service
public class ShopExtrasService {

    static final int MAX_ADDRESSES = 10;
    static final int MAX_WISHES = 100;
    private static final long HOUR = 3_600_000L;

    private final ShopAccountService accounts;
    private final ShopAddressRepository addresses;
    private final ShopWishRepository wishes;
    private final OnlineDeliveryZoneRepository zones;
    private final OnlineListingRepository listings;
    private final OnlineOrderRepository orders;
    private final OnlineStoreService storeService;
    private final InvoiceService invoices;
    private final AuditLogService auditLogService;
    private final OnlineVariants variants;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public ShopExtrasService(ShopAccountService accounts, ShopAddressRepository addresses, ShopWishRepository wishes, OnlineDeliveryZoneRepository zones,
                             OnlineListingRepository listings, OnlineOrderRepository orders, OnlineStoreService storeService, InvoiceService invoices,
                             AuditLogService auditLogService, OnlineVariants variants) {
        this.variants = variants;
        this.accounts = accounts;
        this.addresses = addresses;
        this.wishes = wishes;
        this.zones = zones;
        this.listings = listings;
        this.orders = orders;
        this.storeService = storeService;
        this.invoices = invoices;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ addresses

    @Transactional
    public List<AddressDto> addresses(String token) {
        Long id = accounts.authenticate(token).getId();
        return addresses.findByShopCustomerIdOrderByDefaultAddressDescIdAsc(id).stream().map(ShopExtrasService::dto).toList();
    }

    @Transactional
    public List<AddressDto> saveAddress(String token, Long addressId, AddressRequest in) {
        Long customerId = accounts.authenticate(token).getId();
        String address = in.address() == null ? "" : in.address().trim();
        if (address.isEmpty()) throw ApiException.badRequest(I18n.t("Enter the delivery address."));
        String label = in.label() == null ? "" : in.label().trim();
        if (label.isEmpty()) label = I18n.t("Home");
        if (in.zoneId() != null && !zones.existsById(in.zoneId())) throw ApiException.badRequest(I18n.t("That delivery area does not exist."));
        ShopAddress a;
        if (addressId == null) {
            if (addresses.countByShopCustomerId(customerId) >= MAX_ADDRESSES) {
                throw ApiException.badRequest(I18n.t("You can save up to {0} addresses. Remove one first.", MAX_ADDRESSES));
            }
            a = new ShopAddress();
            a.setShopCustomerId(customerId);
        } else {
            a = addresses.findByIdAndShopCustomerId(addressId, customerId).orElseThrow(() -> ApiException.notFound(I18n.t("Address not found")));
        }
        a.setLabel(label.length() > 60 ? label.substring(0, 60) : label);
        a.setAddress(address.length() > 240 ? address.substring(0, 240) : address);
        a.setZoneId(in.zoneId());
        boolean first = addresses.countByShopCustomerId(customerId) == 0;
        boolean makeDefault = first || Boolean.TRUE.equals(in.isDefault());
        if (makeDefault) {
            for (ShopAddress o : addresses.findByShopCustomerIdOrderByDefaultAddressDescIdAsc(customerId)) {
                if (o.isDefaultAddress() && !o.getId().equals(a.getId())) {
                    o.setDefaultAddress(false);
                    addresses.save(o);
                }
            }
            a.setDefaultAddress(true);
        }
        addresses.save(a);
        return addresses.findByShopCustomerIdOrderByDefaultAddressDescIdAsc(customerId).stream().map(ShopExtrasService::dto).toList();
    }

    @Transactional
    public List<AddressDto> deleteAddress(String token, Long addressId) {
        Long customerId = accounts.authenticate(token).getId();
        ShopAddress a = addresses.findByIdAndShopCustomerId(addressId, customerId).orElseThrow(() -> ApiException.notFound(I18n.t("Address not found")));
        addresses.delete(a);
        addresses.flush();
        List<ShopAddress> left = addresses.findByShopCustomerIdOrderByDefaultAddressDescIdAsc(customerId);
        if (a.isDefaultAddress() && !left.isEmpty() && left.stream().noneMatch(ShopAddress::isDefaultAddress)) {
            left.get(0).setDefaultAddress(true);
            addresses.save(left.get(0));
        }
        return left.stream().map(ShopExtrasService::dto).toList();
    }

    private static AddressDto dto(ShopAddress a) {
        return new AddressDto(a.getId(), a.getLabel(), a.getZoneId(), a.getAddress(), a.isDefaultAddress());
    }

    // ------------------------------------------------------------------ wishlist

    /** The product ids on the wishlist, newest first. */
    @Transactional
    public List<Long> wishlist(String token) {
        Long id = accounts.authenticate(token).getId();
        return wishes.findByShopCustomerIdOrderByIdDesc(id).stream().map(ShopWish::getProductId).toList();
    }

    @Transactional
    public List<Long> addWish(String token, Long productId) {
        Long customerId = accounts.authenticate(token).getId();
        add(customerId, productId);
        return wishes.findByShopCustomerIdOrderByIdDesc(customerId).stream().map(ShopWish::getProductId).toList();
    }

    @Transactional
    public List<Long> removeWish(String token, Long productId) {
        Long customerId = accounts.authenticate(token).getId();
        wishes.findByShopCustomerIdAndProductId(customerId, productId).ifPresent(wishes::delete);
        wishes.flush();
        return wishes.findByShopCustomerIdOrderByIdDesc(customerId).stream().map(ShopWish::getProductId).toList();
    }

    /** The hearts a guest gave in this browser join the account when the customer signs in. Unknown or unlisted products are skipped. */
    @Transactional
    public List<Long> mergeWishes(String token, List<Long> productIds) {
        Long customerId = accounts.authenticate(token).getId();
        if (productIds != null) {
            for (Long p : productIds.stream().limit(MAX_WISHES).toList()) {
                try {
                    add(customerId, p);
                } catch (ApiException ignored) {
                    // a product that is gone, or a full list, just is not added
                }
            }
        }
        return wishes.findByShopCustomerIdOrderByIdDesc(customerId).stream().map(ShopWish::getProductId).toList();
    }

    private void add(Long customerId, Long productId) {
        if (productId == null) throw ApiException.badRequest(I18n.t("Not found."));
        if (wishes.findByShopCustomerIdAndProductId(customerId, productId).isPresent()) return;
        // a product with variants is wished as one product (the template's id)
        boolean listed = listings.findByProductId(productId).filter(OnlineListing::isListed).filter(l -> l.getProduct().isActive()).isPresent() || !variants.rows(productId).isEmpty();
        if (!listed) throw ApiException.notFound(I18n.t("Not found."));
        if (wishes.countByShopCustomerId(customerId) >= MAX_WISHES) throw ApiException.badRequest(I18n.t("Your wishlist is full."));
        ShopWish w = new ShopWish();
        w.setShopCustomerId(customerId);
        w.setProductId(productId);
        wishes.save(w);
    }

    // ------------------------------------------------------------------ invoice

    private static String key(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
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

    /** The invoice PDF of a completed order. The code alone is not enough: the phone number of the order, or the signed-in account that holds the order. */
    @Transactional(readOnly = true)
    public byte[] invoicePdf(String code, String phone, String ip, Long shopCustomerId) {
        OnlineStoreSettings s = storeService.settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        throttle("i:" + ip, 30);
        OnlineOrder o = orders.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        boolean owner = shopCustomerId != null && shopCustomerId.equals(o.getShopCustomerId());
        if (!owner && (key(phone).length() < 6 || !key(phone).equals(o.getPhoneKey()))) {
            throw ApiException.badRequest(I18n.t("The phone number does not match this order."));
        }
        if (o.getInvoiceId() == null) throw ApiException.badRequest(I18n.t("The invoice is ready once your order is completed."));
        return invoices.pdf(o.getInvoiceId());
    }
}
