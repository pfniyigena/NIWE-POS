package com.posapp.onlinestore;

import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Product variants on the shop (v1.50.0). The Products screen already makes variants: a template product ("T-Shirt", kept inactive, with its options written as
 * "Size: S, M, L | Colour: Red, Blue") and one real product per combination ("T-Shirt - Red / L") with its own SKU, price and stock. The shop shows the template as
 * ONE product with option pickers; a customer's cart, the stock, the invoice and the returns all keep working on the chosen variant, exactly as for any product.
 * <p>
 * How it is stored: the template has a listing row (the "master": title, description, specifications, pictures and the front-page ticks for the whole product,
 * and the switch for the whole product) and every variant has its own listing row (its online price, a switch for this option, its own pictures). The master's
 * content is copied onto the variant rows by {@link #sync} so the search and the front-page rows keep working on plain listing rows.
 */
@Component
public class OnlineVariants {

    /** One option of a product with variants: "Size" with the values S, M, L. */
    public record Option(String name, List<String> values) {
    }

    private final ProductRepository products;
    private final OnlineListingRepository listings;

    public OnlineVariants(ProductRepository products, OnlineListingRepository listings) {
        this.products = products;
        this.listings = listings;
    }

    public Optional<Product> template(Long templateId) {
        return products.findById(templateId).filter(p -> p.getVariantAttributes() != null && !p.getVariantAttributes().isBlank());
    }

    /** "Size: S, M, L | Colour: Red, Blue" as options. Anything that does not read that way gives no options (the variants are then shown by their labels). */
    static List<Option> options(Product template) {
        List<Option> out = new ArrayList<>();
        String raw = template == null ? null : template.getVariantAttributes();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("\\s*\\|\\s*")) {
            int c = part.indexOf(':');
            if (c <= 0) continue;
            List<String> values = new ArrayList<>();
            for (String v : part.substring(c + 1).split(",")) {
                String t = v.trim();
                if (!t.isEmpty()) values.add(t);
            }
            if (!values.isEmpty()) out.add(new Option(part.substring(0, c).trim(), values));
        }
        return out;
    }

    /** The option values of a variant, in the order of the options: "Red / L" gives [Red, L]. */
    static List<String> values(Product variant) {
        List<String> out = new ArrayList<>();
        String label = variant.getVariantLabel();
        if (label == null) return out;
        for (String v : label.split("\\s*/\\s*")) {
            if (!v.isBlank()) out.add(v.trim());
        }
        return out;
    }

    /** The options only when every variant has one value per option (otherwise the picker would lie). */
    static boolean consistent(List<Option> options, List<OnlineListing> rows) {
        if (options.isEmpty()) return false;
        for (OnlineListing l : rows) {
            List<String> v = values(l.getProduct());
            if (v.size() != options.size()) return false;
            for (int i = 0; i < v.size(); i++) {
                String wanted = v.get(i);
                if (options.get(i).values().stream().noneMatch(x -> x.equalsIgnoreCase(wanted))) return false;
            }
        }
        return true;
    }

    /** The options the shop offers for a product with variants, in the order of the option values (S, M, L), not alphabetical. */
    @Transactional(readOnly = true)
    public List<OnlineListing> rows(Long templateId) {
        List<OnlineListing> rows = new ArrayList<>(listings.findGroupRows(templateId));
        if (rows.size() < 2) return rows;
        List<Option> options = template(templateId).map(OnlineVariants::options).orElse(List.of());
        if (consistent(options, rows)) {
            rows.sort(Comparator.comparing((OnlineListing l) -> position(options, l.getProduct())).thenComparing(OnlineListing::getId));
        }
        return rows;
    }

    private static List<Integer> positionKey(List<Option> options, Product variant) {
        List<String> v = values(variant);
        List<Integer> key = new ArrayList<>();
        for (int i = 0; i < options.size() && i < v.size(); i++) {
            int at = 0;
            for (int k = 0; k < options.get(i).values().size(); k++) if (options.get(i).values().get(k).equalsIgnoreCase(v.get(i))) at = k;
            key.add(at);
        }
        return key;
    }

    private static String position(List<Option> options, Product variant) {
        StringBuilder sb = new StringBuilder();
        for (int k : positionKey(options, variant)) sb.append(String.format("%04d", k));
        return sb.toString();
    }

    /** The row a visitor first lands on for a product with variants: the first option that can be bought, else the first one. */
    public OnlineListing lead(List<OnlineListing> rows, java.util.function.Predicate<OnlineListing> inStock) {
        return rows.stream().filter(inStock).findFirst().orElse(rows.get(0));
    }

    /**
     * Makes every variant row follow the product's master row: creates the rows of variants that have none, copies the title, description, specifications,
     * front-page ticks and order, and switches a variant on only when the whole product is on and staff did not switch that option off.
     */
    @Transactional
    public void sync(Long templateId) {
        Product template = template(templateId).orElse(null);
        OnlineListing master = listings.findByProductId(templateId).orElse(null);
        if (template == null || master == null) return;
        for (Product v : products.findByVariantParentIdOrderByNameAsc(templateId)) {
            OnlineListing row = listings.findByProductId(v.getId()).orElse(null);
            if (row == null) {
                if (!v.isActive()) continue;
                row = new OnlineListing();
                row.setProduct(v);
            }
            row.setWebTitle(master.getWebTitle());
            row.setWebDescription(master.getWebDescription());
            row.setSpecs(master.getSpecs());
            row.setFeatured(master.isFeatured());
            row.setBestSeller(master.isBestSeller());
            row.setSpecialOffer(master.isSpecialOffer());
            row.setNewArrival(master.isNewArrival());
            row.setSortOrder(master.getSortOrder());
            row.setListed(master.isListed() && !row.isVariantOff());
            listings.save(row);
        }
    }

    /** Every product with variants on the shop is brought up to date (new variants appear, removed ones go). Called by a job and when staff save. */
    @Transactional
    public void syncAll() {
        for (OnlineListing m : listings.findMasters()) sync(m.getProduct().getId());
    }
}
