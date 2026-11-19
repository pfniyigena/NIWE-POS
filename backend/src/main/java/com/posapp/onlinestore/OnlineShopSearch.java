package com.posapp.onlinestore;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
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

    @PersistenceContext
    private EntityManager em;

    /** sort: price_asc, price_desc, newest, rating, popular; anything else keeps staff's own order. */
    public record Filter(String q, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice, boolean inStock, int minRating, String sort) {
    }

    /** The price the customer sees, as an expression: the online or normal price plus tax when the tax is added on top. */
    private static final String SHOWN = "(coalesce(l.onlinePrice, p.unitPrice) * (1 + case when t.id is not null and t.inclusive = false then coalesce(t.ratePercent, 0) / 100 else 0 end))";
    private static final String SELLABLE = "((select coalesce(sum(ps.quantity), 0) from ProductStock ps where ps.product = p and ps.location.active = true "
            + "and (ps.location.type is null or ps.location.type <> com.posapp.warehouse.LocationType.DAMAGED)) "
            + "- (select coalesce(sum(sl.quantityOrdered - sl.quantityFulfilled), 0) from SalesOrderLine sl where sl.product.id = p.id "
            + "and sl.salesOrder.status in (com.posapp.salesorder.SalesOrderStatus.CONFIRMED, com.posapp.salesorder.SalesOrderStatus.PARTIALLY_FULFILLED)))";
    private static final String RATING = "(select coalesce(avg(r.rating), 0) from OnlineReview r where r.productId = p.id and r.status = 'APPROVED')";
    private static final String SOLD = "(select coalesce(sum(ol.quantity), 0) from OnlineOrderLine ol where ol.productId = p.id)";

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

    /** buffer: the shop's safety stock (Shop settings), taken off what may be sold, exactly as the stock word on the card does. */
    public Page<OnlineListing> search(Filter f, int page, int size, int buffer) {
        StringBuilder from = new StringBuilder("from OnlineListing l join l.product p left join p.category c left join p.brand b left join p.taxRate t "
                + "where l.listed = true and p.active = true");
        List<Object[]> params = new ArrayList<>();
        if (f.category() != null && !f.category().isBlank()) {
            from.append(" and c.name = :category");
            params.add(new Object[]{"category", f.category().trim()});
        }
        if (f.brand() != null && !f.brand().isBlank()) {
            from.append(" and b.name = :brand");
            params.add(new Object[]{"brand", f.brand().trim()});
        }
        int i = 0;
        for (String w : words(f.q())) {
            String n = "w" + i++;
            from.append(" and (lower(p.name) like :").append(n).append(" or lower(coalesce(l.webTitle, '')) like :").append(n)
                    .append(" or lower(coalesce(p.sku, '')) like :").append(n).append(" or lower(coalesce(p.barcode, '')) like :").append(n)
                    .append(" or lower(coalesce(b.name, '')) like :").append(n).append(" or lower(coalesce(c.name, '')) like :").append(n)
                    .append(" or lower(coalesce(l.webDescription, p.description, '')) like :").append(n).append(")");
            params.add(new Object[]{n, "%" + w + "%"});
        }
        if (f.minPrice() != null) {
            from.append(" and ").append(SHOWN).append(" >= :minPrice");
            params.add(new Object[]{"minPrice", f.minPrice()});
        }
        if (f.maxPrice() != null) {
            from.append(" and ").append(SHOWN).append(" <= :maxPrice");
            params.add(new Object[]{"maxPrice", f.maxPrice()});
        }
        // Same rule as the stock word on the card: sellable stock (active, undamaged locations) minus what confirmed orders promised minus the shop's buffer.
        if (f.inStock()) {
            from.append(" and (p.trackStock = false or p.service = true or ").append(SELLABLE).append(" - :buffer > 0)");
            params.add(new Object[]{"buffer", java.math.BigDecimal.valueOf(Math.max(0, buffer))});
        }
        if (f.minRating() >= 1) {
            from.append(" and ").append(RATING).append(" >= :minRating");
            params.add(new Object[]{"minRating", (double) Math.min(5, f.minRating())});
        }
        String order = switch (f.sort() == null ? "" : f.sort()) {
            case "price_asc" -> " order by " + SHOWN + " asc, l.id asc";
            case "price_desc" -> " order by " + SHOWN + " desc, l.id asc";
            case "newest" -> " order by l.id desc";
            case "rating" -> " order by " + RATING + " desc, l.id asc";
            case "popular" -> " order by " + SOLD + " desc, l.id asc";
            default -> " order by l.sortOrder asc, l.id asc";
        };
        Query count = em.createQuery("select count(l) " + from);
        Query rows = em.createQuery("select l " + from + order);
        for (Object[] p : params) {
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

    /** Products most often bought in the same orders as this one (best first). */
    @SuppressWarnings("unchecked")
    public List<Long> boughtTogether(Long productId, int limit) {
        List<Object[]> rows = em.createQuery("select l2.productId, count(distinct l2.onlineOrder.id) from OnlineOrderLine l1, OnlineOrderLine l2 "
                        + "where l1.onlineOrder = l2.onlineOrder and l1.productId = :id and l2.productId <> :id and l1.onlineOrder.status not in ('REJECTED', 'CANCELLED') "
                        + "group by l2.productId order by count(distinct l2.onlineOrder.id) desc, l2.productId asc")
                .setParameter("id", productId).setMaxResults(limit).getResultList();
        List<Long> out = new ArrayList<>();
        for (Object[] r : rows) out.add(((Number) r[0]).longValue());
        return out;
    }
}
