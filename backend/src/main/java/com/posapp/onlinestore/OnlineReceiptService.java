package com.posapp.onlinestore;

import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.CustomerReceivedRequest;
import com.posapp.onlinestore.OnlineStoreDtos.ReceiptInfo;
import com.posapp.onlinestore.OnlineStoreDtos.Track;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The customer's side of order reception (v1.56.0): after proving the order is theirs (signed in to the account that holds it, or the phone number of
 * the order) the customer can see the delivery code to give to the driver, and tap "I received my order" (with a signature if they want), which
 * completes the order. Staff side: {@link OnlineOrderService#receive}.
 */
@Service
public class OnlineReceiptService {

    private static final long HOUR = 3_600_000L;

    private final OnlineOrderRepository orders;
    private final OnlineOrderService orderService;
    private final OnlineStoreService storeService;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public OnlineReceiptService(OnlineOrderRepository orders, OnlineOrderService orderService, OnlineStoreService storeService) {
        this.orders = orders;
        this.orderService = orderService;
        this.storeService = storeService;
    }

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

    /** The order of this code, when the caller proves it is theirs. The code alone is not enough: anybody holding the slip knows it. */
    private OnlineOrder owned(String code, String phone, String ip, Long shopCustomerId) {
        if (!storeService.settings().isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        throttle("r:" + ip, 40);
        OnlineOrder o = orders.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        boolean owner = shopCustomerId != null && shopCustomerId.equals(o.getShopCustomerId());
        if (!owner && (key(phone).length() < 6 || !key(phone).equals(o.getPhoneKey()))) {
            throw ApiException.badRequest(I18n.t("The phone number does not match this order."));
        }
        return o;
    }

    /** The delivery code (while the goods are out or ready) or the reception details (after). */
    @Transactional
    public ReceiptInfo info(String code, String phone, String ip, Long shopCustomerId) {
        OnlineOrder o = owned(code, phone, ip, shopCustomerId);
        boolean waiting = "OUT_FOR_DELIVERY".equals(o.getStatus()) || "READY_FOR_PICKUP".equals(o.getStatus());
        return new ReceiptInfo(o.getStatus(), waiting, waiting ? o.ensureReceiptCode() : null, o.getReceivedAt(), o.getReceivedVia(), o.getSignedName());
    }

    /** "I received my order": completes the order. Only while it is out for delivery or ready for pick-up. */
    public Track confirm(String code, CustomerReceivedRequest in, String ip, Long shopCustomerId) {
        OnlineOrder o = owned(code, in == null ? null : in.phone(), ip, shopCustomerId);
        if (!"OUT_FOR_DELIVERY".equals(o.getStatus()) && !"READY_FOR_PICKUP".equals(o.getStatus())) {
            throw ApiException.badRequest(o.getReceivedAt() != null || "COMPLETED".equals(o.getStatus())
                    ? I18n.t("This order is already complete.") : I18n.t("Your order is not on its way yet."));
        }
        orderService.receiveByCustomer(o.getId(), in == null ? null : in.signature(), in == null ? null : in.signedName());
        return storeService.trackByCode(o.getCode(), ip);
    }
}
