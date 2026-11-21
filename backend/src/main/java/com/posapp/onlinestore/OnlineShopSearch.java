package com.posapp.onlinestore;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The shop's product search (v1.45.0). Every word typed must be found somewhere in the product: its name, web title, SKU, barcode, brand, category or
 * description, so a visitor who does not know the exact name still finds it. It can also filter by category, brand, price range (as the customer sees the
 * price, tax included), availability and the average rating of the approved reviews, and sort. Everything is done in the database, a page at a time.
 */
@Component
public class OnlineShopSearch {

    static final int MAX_WORDS = 5;

    @Autowired
    private OnlineShopStock shopStock;

    @PersistenceContext
    private EntityManager em;

    /** sort: price_asc, price_desc, newest, rating, popular; anything else keeps staff's own order. */
    public record Filter(String q, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice, boolean inStock, int minRating, String sort) {
    }

    /** The price the customer sees as an expression over the aliases of a listing (l), its product (p) and its tax rate (t). */
    private static String shown(String l, String p, String t) {
        return "(coalesce(" + l + ".onlinePrice, " + p + ".unitPrice) * (1 + case when " + t + ".id is not null and " + t + ".inclusive = false then coalesce(" + t + ".ratePercent, 0) / 100 else 0 end))";
    }

    private static final String SELLABLE = "((select coalesce(sum(ps.quantity), 0) from ProductStock ps where ps.product = %1$s and ps.location.active = true%2$s "
            + "and (ps.location.type is null or ps.location.type <> com.posapp.warehouse.LocationType.DAMAGED)) "
            + "- (select coalesce(sum(sl.quantityOrdered - sl.quantityFulfilled), 0) from SalesOrderLine sl where sl.product.id = %1$s.id "
            + "and sl.salesOrder.status in (com.posapp.salesorder.SalesOrderStatus.CONFIRMED, com.posapp.salesorder.SalesOrderStatus.PARTIALLY_FULFILLED)) "
            // v1.66.0: shop orders staff have not answered yet hold their units too
            + "- (select coalesce(sum(ol0.quantity), 0) from OnlineOrderLine ol0 where ol0.productId = %1$s.id and ol0.onlineOrder.status in ('AWAITING_PAYMENT', 'PLACED')))";
    /** A product with variants is rated and counted as one product: the ratings and the sales of all its variants. */
    private static final String SAME_GROUP = "coalesce(q.variantParentId, q.id) = coalesce(p.variantParentId, p.id)";
    private static final String RATING = "(select coalesce(avg(r.rating), 0) from OnlineReview r where r.ratingCounted = true "
            + "and r.productId in (select q.id from Product q where " + SAME_GROUP + "))";
    private static final String SOLD = "(select coalesce(sum(ol.quantity), 0) from OnlineOrderLine ol where ol.productId in (select q.id from Product q where " + SAME_GROUP + "))";
    /** The lowest price among the listed options of the product (a plain product: its own price), for sorting by price. */
    private static final String LOWEST = "(select min(" + shown("l3", "q", "t3") + ") from OnlineListing l3 join l3.product q left join q.taxRate t3 "
            + "where l3.listed = true and q.active = true and " + SAME_GROUP + ")";

    /** The words of a search: lower case, without the characters that mean something to LIKE, at most MAX_WORDS. */
    static List<String> words(String q) {
        List<String> out = new ArrayList<>();
        if (q == null) return out;
        String clean = q.toLowerCase().replace("\\", " ").replace("%", " ").replace("_", " ").trim();
        if (clean.length() > 80) clean = clean.substring(0, 80);
        for (String w : clean.split("\\s+")) {
            if (!w.isEmpty() && out.size() < MAX_WORDS) out.add(w);
        }
        return out;
    }

    /**
     * One page of the shop. A product with variants is ONE result: the first option that matches the search and the filters stands for it, so a page
     * holds the number of products asked for and a product is never listed once per size and colour. buffer: the shop's safety stock (Shop settings),
     * taken off what may be sold, exactly as the stock word on the card does.
     */
    public Page<OnlineListing> search(Filter f, int page, int size, int buffer) {
        List<Object[]> params = new ArrayList<>();
        String here = conditions(f, buffer, "l", "p", "c", "b", "t", params, true);
        String there = conditions(f, buffer, "l2", "p2", "c2", "b2", "t2", params, false);
        String from = "from OnlineListing l join l.product p left join p.category c left join p.brand b left join p.taxRate t where " + here
                + " and (p.variantParentId is null or l.id = (select min(l2.id) from OnlineListing l2 join l2.product p2 left join p2.category c2 left join p2.brand b2 "
                + "left join p2.taxRate t2 where " + there + " and p2.variantParentId = p.variantParentId))";
        String order = switch (f.sort() == null ? "" : f.sort()) {
            case "price_asc" -> " order by " + LOWEST + " asc, l.id asc";
            case "price_desc" -> " order by " + LOWEST + " desc, l.id asc";
            case "newest" -> " order by l.id desc";
            case "rating" -> " order by " + RATING + " desc, l.id asc";
            case "popular" -> " order by " + SOLD + " desc, l.id asc";
            default -> " order by l.sortOrder asc, l.id asc";
        };
        Query count = em.createQuery("select count(l) " + from);
        Query rows = em.createQuery("select l " + from + order);
        java.util.Set<String> done = new java.util.HashSet<>();
        for (Object[] p : params) {
            if (!done.add((String) p[0])) continue;
            count.setParameter((String) p[0], p[1]);
            rows.setParameter((String) p[0], p[1]);
        }
        long total = ((Number) count.getSingleResult()).longValue();
        rows.setFirstResult(page * size);
        rows.setMaxResults(size);
        @SuppressWarnings("unchecked")
        List<OnlineListing> content = rows.getResultList();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    /** The WHERE conditions of a search for the listing, product, category, brand and tax rate under the given aliases. Parameters are collected once. */
    private String conditions(Filter f, int buffer, String l, String p, String c, String b, String t, List<Object[]> params, boolean collect) {
        StringBuilder w = new StringBuilder(l + ".listed = true and " + p + ".active = true");
        List<Object[]> sink = collect ? params : new ArrayList<>();
        if (f.category() != null && !f.category().isBlank()) {
            w.append(" and ").append(c).append(".name = :category");
            sink.add(new Object[]{"category", f.category().trim()});
        }
        if (f.brand() != null && !f.brand().isBlank()) {
            w.append(" and ").append(b).append(".name = :brand");
            sink.add(new Object[]{"brand", f.brand().trim()});
        }
        int i = 0;
        for (String word : words(f.q())) {
            String n = "w" + i++;
            w.append(" and (lower(").append(p).append(".name) like :").append(n).append(" or lower(coalesce(").append(l).append(".webTitle, '')) like :").append(n)
                    .append(" or lower(coalesce(").append(p).append(".sku, '')) like :").append(n).append(" or lower(coalesce(").append(p).append(".barcode, '')) like :").append(n)
                    .append(" or lower(coalesce(").append(b).append(".name, '')) like :").append(n).append(" or lower(coalesce(").append(c).append(".name, '')) like :").append(n)
                    .append(" or lower(coalesce(").append(l).append(".webDescription, ").append(p).append(".description, '')) like :").append(n).append(")");
            sink.add(new Object[]{n, "%" + word + "%"});
        }
        if (f.minPrice() != null) {
            w.append(" and ").append(shown(l, p, t)).append(" >= :minPrice");
            sink.add(new Object[]{"minPrice", f.minPrice()});
        }
        if (f.maxPrice() != null) {
            w.append(" and ").append(shown(l, p, t)).append(" <= :maxPrice");
            sink.add(new Object[]{"maxPrice", f.maxPrice()});
        }
        // Same rule as the stock word on the card: sellable stock (active, undamaged locations) minus what confirmed orders promised minus the shop's buffer.
        if (f.inStock()) {
            String avail = String.format(SELLABLE, p, shopStock.searchCondition());
            w.append(" and (").append(p).append(".trackStock = false or ").append(p).append(".service = true or ").append(avail).append(" - coalesce(").append(l)
                    .append(".stockBuffer, :buffer) - ceiling(").append(avail).append(" * :till / 100.0) > 0)");
            sink.add(new Object[]{"buffer", Math.max(0, buffer)});
            sink.add(new Object[]{"till", java.math.BigDecimal.valueOf(Math.max(0, shopStock.tillPercent()))});
        }
        if (f.minRating() >= 1) {
            // the rating of the product as a whole (all its variants); the alias used in RATING is p, so the conditions of the inner query use p2 through a copy
            w.append(" and ").append(RATING.replace("p.variantParentId", p + ".variantParentId").replace("p.id", p + ".id")).append(" >= :minRating");
            sink.add(new Object[]{"minRating", (double) Math.min(5, f.minRating())});
        }
        return w.toString();
    }

    /** Products most often bought in the same orders as these (the options of one product together), best first. */
    @SuppressWarnings("unchecked")
    public List<Long> boughtTogether(java.util.Collection<Long> productIds, int limit) {
        List<Object[]> rows = em.createQuery("select l2.productId, count(distinct l2.onlineOrder.id) from OnlineOrderLine l1, OnlineOrderLine l2 "
                        + "where l1.onlineOrder = l2.onlineOrder and l1.productId in :ids and l2.productId not in :ids and l1.onlineOrder.status not in ('REJECTED', 'CANCELLED') "
                        + "group by l2.productId order by count(distinct l2.onlineOrder.id) desc, l2.productId asc")
                .setParameter("ids", productIds).setMaxResults(limit).getResultList();
        List<Long> out = new ArrayList<>();
        for (Object[] r : rows) out.add(((Number) r[0]).longValue());
        return out;
    }
}
