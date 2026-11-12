package com.posapp.onlinestore;

import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shop's front page (v1.35.0): the rows of products staff picked (featured, best-selling, special offers, new arrivals), the banner pictures and
 * the best of the approved reviews, plus the star rating that goes on a product. Nothing here changes anything.
 */
@Service
public class OnlineFrontService {

    static final int ROW = 8;

    private final OnlineStoreService store;
    private final OnlineListingRepository listings;
    private final OnlineReviewRepository reviews;
    private final OnlineBannerService banners;

    public OnlineFrontService(OnlineStoreService store, OnlineListingRepository listings, OnlineReviewRepository reviews, OnlineBannerService banners) {
        this.banners = banners;
        this.store = store;
        this.listings = listings;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public PublicHome home() {
        OnlineStoreSettings s = store.settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        PageRequest top = PageRequest.of(0, ROW);
        List<OnlineStoreDtos.PublicProduct> featured = row(listings.findFeatured(top), s);
        List<OnlineStoreDtos.PublicProduct> best = row(listings.findBestSellers(top), s);
        List<OnlineStoreDtos.PublicProduct> offers = row(listings.findOffers(top), s);
        List<OnlineStoreDtos.PublicProduct> fresh = row(listings.findNewArrivals(top), s);
        List<PublicReview> shown = reviews.findShown(PageRequest.of(0, 6)).stream()
                .map(r -> new PublicReview(r.getProductName(), r.getReviewerName(), r.getRating(), r.getReviewText(), r.getCreatedAt())).toList();
        Double avg = null;
        long count = 0;
        List<Object[]> overall = reviews.overall();
        if (!overall.isEmpty() && overall.get(0)[0] != null) {
            avg = round1(((Number) overall.get(0)[0]).doubleValue());
            count = ((Number) overall.get(0)[1]).longValue();
        }
        return new PublicHome(featured, best, offers, fresh, banners.urls(), shown, avg, count);
    }

    private List<OnlineStoreDtos.PublicProduct> row(List<OnlineListing> rows, OnlineStoreSettings s) {
        return withRatings(rows.stream().map(l -> store.publicProduct(l, s)).toList());
    }

    /** The same products with their star rating (average of the approved reviews, and how many). Products with no review keep 0. */
    @Transactional(readOnly = true)
    public List<OnlineStoreDtos.PublicProduct> withRatings(List<OnlineStoreDtos.PublicProduct> in) {
        if (in.isEmpty()) return in;
        Map<Long, double[]> by = new HashMap<>();
        for (Object[] r : reviews.ratings(in.stream().map(OnlineStoreDtos.PublicProduct::id).toList())) {
            by.put(((Number) r[0]).longValue(), new double[]{((Number) r[1]).doubleValue(), ((Number) r[2]).doubleValue()});
        }
        List<OnlineStoreDtos.PublicProduct> out = new ArrayList<>();
        for (OnlineStoreDtos.PublicProduct p : in) {
            double[] x = by.get(p.id());
            out.add(x == null ? p : new OnlineStoreDtos.PublicProduct(p.id(), p.name(), p.description(), p.price(), p.category(), p.imageUrl(), p.stock(),
                    p.wasPrice(), round1(x[0]), (int) x[1]));
        }
        return out;
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
