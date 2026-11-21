package com.posapp.onlinestore;

import com.posapp.catalog.Product;
import com.posapp.pricing.PriceList;
import com.posapp.pricing.PriceListEntry;
import com.posapp.pricing.PriceListEntryRepository;
import com.posapp.pricing.PriceListRepository;
import com.posapp.promotions.Promotion;
import com.posapp.promotions.PromotionRepository;
import com.posapp.promotions.PromotionScope;
import com.posapp.promotions.PromotionType;
import com.posapp.salessettings.SalesSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.69.0: the shop price of a product that staff gave no online price. It starts from the normal selling price, moves to the price of the shop's price list
 * (Shop settings), then takes off the best promotion of the ERP that anybody may have: a promotion on the product or its category, switched on, inside its
 * dates, with no coupon code, no customer group, no limit on uses, no minimum quantity or spend, and of the kind "percent off" or "amount off". Offers that need
 * a quantity (buy 2 get 1, from 5 units), a coupon or a whole cart are not applied by the shop.
 *
 * <p>The result is written on the listing ({@code offerPrice}) so the card, the cart, the order and the search by price all read the same figure. It is worked out
 * again every two minutes, and when staff change the shop's listings or settings, so a change made in Sales shows in the shop within two minutes.</p>
 */
@Component
public class OnlineOffers {

    private static final Logger log = LoggerFactory.getLogger(OnlineOffers.class);

    @Autowired
    private OnlineListingRepository listings;
    @Autowired
    private OnlineStoreSettingsRepository settingsRepository;
    @Autowired
    private PromotionRepository promotions;
    @Autowired
    private PriceListRepository priceLists;
    @Autowired
    private PriceListEntryRepository priceEntries;
    @Autowired
    private SalesSettingsService features;

    /** A price and what it comes from. */
    public record Offer(BigDecimal price, String note) {
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    @Scheduled(fixedDelay = 120_000L, initialDelay = 90_000L)
    public void run() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("Refreshing the shop offers failed: {}", e.getMessage());
        }
    }

    /** What the offers are worked out from: the shop's price list (when current), its entries by product, and the promotions anybody may have. */
    private record Basis(PriceList list, Map<Long, List<PriceListEntry>> entries, List<Promotion> usable) {
    }

    private Basis basis() {
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        LocalDate today = LocalDate.now();
        PriceList list = null;
        if (s != null && s.getPriceListId() != null) list = priceLists.findById(s.getPriceListId()).filter(l -> l.isCurrent(today)).orElse(null);
        Map<Long, List<PriceListEntry>> entries = new HashMap<>();
        if (list != null) for (PriceListEntry e : priceEntries.findByPriceListId(list.getId())) entries.computeIfAbsent(e.getProductId(), k -> new ArrayList<>()).add(e);
        List<Promotion> usable = new ArrayList<>();
        if (features.promotionsOn()) for (Promotion p : promotions.findAll()) if (anybodys(p, today)) usable.add(p);
        return new Basis(list, entries, usable);
    }

    /** Works the offer price out again for every listing. Returns how many listings changed. */
    @Transactional
    public int refresh() {
        Basis b = basis();
        int changed = 0;
        for (OnlineListing l : listings.findAll()) if (apply(l, b)) changed++;
        return changed;
    }

    /** The same for one listing (after staff changed it). */
    @Transactional
    public void refreshOne(OnlineListing l) {
        apply(l, basis());
    }

    private boolean apply(OnlineListing l, Basis b) {
        Offer o = l.getOnlinePrice() != null ? null : offerFor(l.getProduct(), b.list(), b.entries().get(l.getProduct().getId()), b.usable());
        BigDecimal price = o == null ? null : o.price();
        String note = o == null ? null : o.note();
        boolean same = (price == null ? l.getOfferPrice() == null : l.getOfferPrice() != null && l.getOfferPrice().compareTo(price) == 0)
                && java.util.Objects.equals(note, l.getOfferNote());
        if (same) return false;
        l.setOfferPrice(price);
        l.setOfferNote(note);
        listings.save(l);
        return true;
    }

    /** A promotion every shop visitor may have: no coupon, no customer group, no limits, no minimum, a price promotion on a product or a category. */
    static boolean anybodys(Promotion p, LocalDate today) {
        if (!p.isActive() || !p.isInDates(today)) return false;
        if (p.getScope() == PromotionScope.CART || p.getType() == PromotionType.BUY_X_GET_Y) return false;
        if (p.getCouponCode() != null && !p.getCouponCode().isBlank()) return false;
        if (p.getCustomerGroupId() != null || p.getMaxUses() != null || p.getMaxUsesPerCustomer() != null) return false;
        if (p.getMinSpend() != null && p.getMinSpend().signum() > 0) return false;
        if (p.getMinQuantity() != null && p.getMinQuantity().compareTo(BigDecimal.ONE) > 0) return false;
        return p.getTargetId() != null && p.getValue() != null && p.getValue().signum() > 0;
    }

    /** The offer for one product, or null when the normal price stands. */
    static Offer offerFor(Product product, PriceList list, List<PriceListEntry> entries, List<Promotion> usable) {
        BigDecimal base = money(product.getUnitPrice() == null ? BigDecimal.ZERO : product.getUnitPrice());
        BigDecimal price = base;
        List<String> notes = new ArrayList<>();
        if (list != null) {
            PriceListEntry best = null;
            if (entries != null) {
                for (PriceListEntry e : entries) {
                    if (e.getMinQuantity().compareTo(BigDecimal.ONE) <= 0 && (best == null || e.getMinQuantity().compareTo(best.getMinQuantity()) > 0)) best = e;
                }
            }
            BigDecimal p;
            if (best != null) p = best.getPrice();
            else if (list.getAdjustPercent() != null && list.getAdjustPercent().signum() != 0) {
                p = base.multiply(BigDecimal.ONE.add(list.getAdjustPercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
            } else p = base;
            p = money(p.max(BigDecimal.ZERO));
            if (p.compareTo(base) != 0) {
                price = p;
                notes.add(list.getName());
            }
        }
        Promotion bestPromo = null;
        BigDecimal bestSaving = BigDecimal.ZERO;
        for (Promotion p : usable) {
            boolean match = p.getScope() == PromotionScope.PRODUCT ? p.getTargetId().equals(product.getId())
                    : product.getCategory() != null && p.getTargetId().equals(product.getCategory().getId());
            if (!match) continue;
            BigDecimal saving = p.getType() == PromotionType.PERCENT_OFF ? price.multiply(p.getValue()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP) : p.getValue();
            saving = saving.min(price);
            if (saving.compareTo(bestSaving) > 0) {
                bestSaving = saving;
                bestPromo = p;
            }
        }
        if (bestPromo != null) {
            price = money(price.subtract(bestSaving).max(BigDecimal.ZERO));
            notes.add(bestPromo.getName());
        }
        if (price.compareTo(base) == 0) return null;
        String note = String.join(" + ", notes);
        return new Offer(price, note.length() > 160 ? note.substring(0, 160) : note);
    }
}
