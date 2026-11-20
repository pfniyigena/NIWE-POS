package com.posapp.onlinestore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.InfoLink;
import com.posapp.onlinestore.OnlineStoreDtos.InfoPageDto;
import com.posapp.onlinestore.OnlineStoreDtos.PublicProduct;
import com.posapp.onlinestore.OnlineStoreDtos.PublicShop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search-engine and share-preview support (v1.53.0). The shop is an Angular app, and the crawlers of WhatsApp, Facebook, Google and others read only
 * the first HTML they get, so for them the web server (nginx, see docs/online-store-seo.md) asks this service for a small page that carries the title,
 * description, picture, price and Google product data of what the link points to. People never see these pages: they get the normal app.
 * The sitemap lists the pages worth indexing and robots.txt keeps crawlers out of the staff screens, orders and the API.
 */
@Service
public class OnlineSeoService {

    static final int MAX_URLS = 5000;
    static final int DESCRIPTION_LENGTH = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OnlineStoreService store;
    private final OnlineProductPageService pages;
    private final OnlineInfoService info;
    private final OnlineListingRepository listings;

    public OnlineSeoService(OnlineStoreService store, OnlineProductPageService pages, OnlineInfoService info, OnlineListingRepository listings) {
        this.store = store;
        this.pages = pages;
        this.info = info;
        this.listings = listings;
    }

    // ------------------------------------------------------------------ the pages crawlers read

    @Transactional(readOnly = true)
    public String shopPage(String origin) {
        PublicShop shop = store.shop();
        String base = base(origin);
        String description = firstNonBlank(shop.intro(), shop.page() == null ? null : shop.page().aboutText(), I18n.t("Shop online with {0}.", shop.name()));
        String image = shop.page() == null || shop.page().heroImageUrl() == null ? null : absolute(base, shop.page().heroImageUrl());
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "OnlineStore");
        ld.put("name", shop.name());
        ld.put("url", base + "/shop");
        if (shop.phone() != null && !shop.phone().isBlank()) ld.put("telephone", shop.phone());
        if (image != null) ld.put("image", image);
        return html(shop.name(), shop.name(), description, base + "/shop", image, "website", shop.name(), null, ld, shop.intro());
    }

    @Transactional(readOnly = true)
    public String productPage(Long id, String origin) {
        PublicShop shop = store.shop();
        String base = base(origin);
        OnlineProductPageService.ProductDetail d = pages.detail(id);
        PublicProduct p = d.product();
        String url = base + "/shop?product=" + p.id();
        String description = shorten(firstNonBlank(p.description(), p.name()));
        List<String> images = new ArrayList<>();
        for (String i : d.images()) images.add(absolute(base, i));
        String image = images.isEmpty() ? null : images.get(0);
        String price = OnlineStoreService.money(p.price());

        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "Product");
        ld.put("name", p.name());
        if (!description.isBlank()) ld.put("description", description);
        if (!images.isEmpty()) ld.put("image", images);
        if (d.brand() != null && !d.brand().isBlank()) ld.put("brand", Map.of("@type", "Brand", "name", d.brand()));
        if (p.category() != null && !p.category().isBlank()) ld.put("category", p.category());
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", p.variants() > 1 ? "AggregateOffer" : "Offer");
        offer.put(p.variants() > 1 ? "lowPrice" : "price", price);
        offer.put("priceCurrency", shop.currency());
        offer.put("availability", "SOLD_OUT".equals(p.stock()) ? "https://schema.org/OutOfStock" : "https://schema.org/InStock");
        offer.put("url", url);
        ld.put("offers", offer);
        if (d.rating() != null && d.rating().count() > 0) {
            ld.put("aggregateRating", Map.of("@type", "AggregateRating", "ratingValue", d.rating().average(), "reviewCount", d.rating().count()));
        }
        String body = (p.priceFrom() ? I18n.t("From") + " " : "") + price + " " + shop.currency()
                + ("SOLD_OUT".equals(p.stock()) ? " · " + I18n.t("Out of stock") : "") + (description.isBlank() ? "" : "\n" + description);
        String title = p.name() + " | " + shop.name();
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("product:price:amount", price);
        extra.put("product:price:currency", shop.currency());
        extra.put("product:availability", "SOLD_OUT".equals(p.stock()) ? "out of stock" : "in stock");
        return html(title, p.name(), description, url, image, "product", shop.name(), extra, ld, body);
    }

    @Transactional(readOnly = true)
    public String infoPage(String slug, String language, String origin) {
        PublicShop shop = store.shop();
        String base = base(origin);
        InfoPageDto page = info.page(slug, language);
        String url = base + "/shop/info/" + page.slug();
        String description = shorten(page.body().replaceAll("(?m)^(##|-)\\s*", ""));
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "WebPage");
        ld.put("name", page.title());
        ld.put("url", url);
        return html(page.title() + " | " + shop.name(), page.title(), description, url, null, "website", shop.name(), null, ld, page.body());
    }

    // ------------------------------------------------------------------ sitemap and robots

    @Transactional(readOnly = true)
    public String sitemap(String origin) {
        store.shop(); // refuses when the shop is not available
        String base = base(origin);
        List<String> urls = new ArrayList<>();
        urls.add(base + "/shop");
        for (InfoLink l : info.links("en")) urls.add(base + "/shop/info/" + l.slug());
        Set<Long> masters = new LinkedHashSet<>();
        for (OnlineListing m : listings.findMasters()) if (m.isListed()) masters.add(m.getProduct().getId());
        Set<Long> ids = new LinkedHashSet<>();
        for (OnlineListing l : listings.findByListedTrueOrderBySortOrderAscIdAsc()) {
            if (!l.getProduct().isActive()) continue;
            Long group = l.getProduct().getVariantParentId();
            if (group == null) ids.add(l.getProduct().getId());
            else if (masters.contains(group)) ids.add(group);
        }
        for (Long id : ids) {
            if (urls.size() >= MAX_URLS) break;
            urls.add(base + "/shop?product=" + id);
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String u : urls) sb.append("  <url><loc>").append(esc(u)).append("</loc></url>\n");
        return sb.append("</urlset>\n").toString();
    }

    public String robots(String origin) {
        String base = base(origin);
        return "User-agent: *\n"
                + "Allow: /shop\n"
                + "Allow: /api/public/store/products/\n"
                + "Allow: /api/public/store/product-images/\n"
                + "Disallow: /shop/account\n"
                + "Disallow: /shop/order/\n"
                + "Disallow: /shop/cart/\n"
                + "Disallow: /shop/pay-test/\n"
                + "Disallow: /shop/newsletter/\n"
                + "Disallow: /driver/\n"
                + "Disallow: /admin\n"
                + "Disallow: /api/\n"
                + "Disallow: /login\n"
                + (base.isEmpty() ? "" : "\nSitemap: " + base + "/sitemap.xml\n");
    }

    // ------------------------------------------------------------------ building blocks

    /** The address of the app: the one set for the e-mail links, else the address the request came in on. */
    static String base(String origin) {
        String set = OnlineMessages.baseUrl();
        if (!set.isEmpty()) return set;
        return origin == null ? "" : origin.replaceAll("/+$", "");
    }

    private static String absolute(String base, String path) {
        if (path == null) return null;
        String p = path.startsWith("http") ? path : base + path;
        return p.contains("?") ? p : p + "?w=1200";
    }

    private static String shorten(String text) {
        if (text == null) return "";
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() <= DESCRIPTION_LENGTH) return t;
        int cut = t.lastIndexOf(' ', DESCRIPTION_LENGTH);
        return t.substring(0, cut > 80 ? cut : DESCRIPTION_LENGTH).trim() + "…";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return "";
    }

    private String html(String title, String heading, String description, String url, String image, String type, String siteName, Map<String, String> extra,
                        Map<String, Object> structured, String body) {
        String desc = shorten(description);
        StringBuilder sb = new StringBuilder("<!doctype html>\n<html lang=\"").append(esc(I18n.french() ? "fr" : "en")).append("\"><head><meta charset=\"utf-8\">\n");
        sb.append("<title>").append(esc(title)).append("</title>\n");
        sb.append(meta("name", "description", desc));
        sb.append("<link rel=\"canonical\" href=\"").append(esc(url)).append("\">\n");
        sb.append(meta("property", "og:type", type)).append(meta("property", "og:site_name", siteName)).append(meta("property", "og:title", heading))
                .append(meta("property", "og:description", desc)).append(meta("property", "og:url", url));
        if (image != null) sb.append(meta("property", "og:image", image));
        sb.append(meta("name", "twitter:card", image != null ? "summary_large_image" : "summary")).append(meta("name", "twitter:title", heading))
                .append(meta("name", "twitter:description", desc));
        if (image != null) sb.append(meta("name", "twitter:image", image));
        if (extra != null) for (Map.Entry<String, String> e : extra.entrySet()) sb.append(meta("property", e.getKey(), e.getValue()));
        try {
            // "</" inside a script block would end it early: the one character that must never appear as written
            sb.append("<script type=\"application/ld+json\">").append(JSON.writeValueAsString(structured).replace("</", "<\\/")).append("</script>\n");
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Could not build the page");
        }
        sb.append("</head><body>\n<h1>").append(esc(heading)).append("</h1>\n");
        if (image != null) sb.append("<p><img src=\"").append(esc(image)).append("\" alt=\"").append(esc(heading)).append("\" width=\"400\"></p>\n");
        if (body != null) for (String line : body.split("\n")) if (!line.isBlank()) sb.append("<p>").append(esc(line.replaceAll("^(##|-)\\s*", ""))).append("</p>\n");
        sb.append("<p><a href=\"").append(esc(url)).append("\">").append(esc(I18n.t("Open in the shop"))).append("</a></p>\n</body></html>\n");
        return sb.toString();
    }

    private static String meta(String attr, String key, String value) {
        return value == null || value.isBlank() ? "" : "<meta " + attr + "=\"" + esc(key) + "\" content=\"" + esc(value) + "\">\n";
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
