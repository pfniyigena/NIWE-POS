package com.posapp.tracking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-request hints for the stock chokepoint: which serial numbers a sale chose, and the document
 * number a sale belongs to. Stock code that does not know about tracking keeps working untouched —
 * the callers that do know (checkout, sales-order fulfilment) set this before they reduce stock and
 * clear it afterwards.
 */
public final class TrackingContext {

    private static final ThreadLocal<Map<Long, List<String>>> SERIALS = new ThreadLocal<>();
    private static final ThreadLocal<String> SALE_REFERENCE = new ThreadLocal<>();

    private TrackingContext() {}

    public static void begin(String saleReference, Map<Long, List<String>> serialsByProduct) {
        SALE_REFERENCE.set(saleReference);
        SERIALS.set(serialsByProduct != null ? new HashMap<>(serialsByProduct) : null);
    }

    public static void clear() {
        SALE_REFERENCE.remove();
        SERIALS.remove();
    }

    public static String saleReference() {
        return SALE_REFERENCE.get();
    }

    /** The serials chosen for this product (removed so a second line of the same product auto-picks), or null. */
    public static List<String> takeSerials(Long productId) {
        Map<Long, List<String>> map = SERIALS.get();
        return map == null ? null : map.remove(productId);
    }
}
